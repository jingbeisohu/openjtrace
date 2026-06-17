package org.openjtrace.parser.java;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.util.*;

public class JavaSourceParser {

    public static class JavaMethodMeta {
        private String name;
        private Map<String, String> annotations = new HashMap<>();
        private List<MethodCall> methodCalls = new ArrayList<>();

        public String getName() {
            return name;
        }

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public List<MethodCall> getMethodCalls() {
            return methodCalls;
        }
    }

    public static class MethodCall {
        private String scope; // e.g. "userService" in "userService.getUser()"
        private String name;  // e.g. "getUser"
        private String resolvedClass; // 推断出的类全限定名，若无法推断则为 scope 变量名
        private List<String> arguments = new ArrayList<>();

        public MethodCall(String scope, String name) {
            this.scope = scope;
            this.name = name;
        }

        public String getScope() {
            return scope;
        }

        public String getName() {
            return name;
        }

        public String getResolvedClass() {
            return resolvedClass;
        }

        public void setResolvedClass(String resolvedClass) {
            this.resolvedClass = resolvedClass;
        }

        public List<String> getArguments() {
            return arguments;
        }
    }

    public static class JavaClassMeta {
        private String packageName = "";
        private String className;
        private String qualifiedName;
        private boolean isInterface;
        private Map<String, String> annotations = new HashMap<>();
        private List<JavaMethodMeta> methods = new ArrayList<>();
        private Map<String, String> fieldTypes = new HashMap<>(); // fieldName -> ClassType
        private File file;

        public String getPackageName() {
            return packageName;
        }

        public String getClassName() {
            return className;
        }

        public String getQualifiedName() {
            return qualifiedName;
        }

        public boolean isInterface() {
            return isInterface;
        }

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public List<JavaMethodMeta> getMethods() {
            return methods;
        }

        public Map<String, String> getFieldTypes() {
            return fieldTypes;
        }

        public File getFile() {
            return file;
        }
    }

    public List<JavaClassMeta> parseDirectory(File dir) {
        List<JavaClassMeta> list = new ArrayList<>();
        findAndParseJavaFiles(dir, list);
        return list;
    }

    private void findAndParseJavaFiles(File file, List<JavaClassMeta> list) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    findAndParseJavaFiles(child, list);
                }
            }
        } else if (file.getName().endsWith(".java")) {
            try {
                JavaClassMeta meta = parseJavaFile(file);
                if (meta != null) {
                    list.add(meta);
                }
            } catch (Exception e) {
                // Keep robust for scanner
            }
        }
    }

    public JavaClassMeta parseJavaFile(File file) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        // 我们只解析文件中的第一个公有 ClassOrInterface
        Optional<ClassOrInterfaceDeclaration> classOpt = cu.findFirst(ClassOrInterfaceDeclaration.class);
        if (!classOpt.isPresent()) {
            return null;
        }

        ClassOrInterfaceDeclaration classDecl = classOpt.get();
        JavaClassMeta classMeta = new JavaClassMeta();
        classMeta.file = file;
        classMeta.isInterface = classDecl.isInterface();
        classMeta.className = classDecl.getNameAsString();
        
        cu.getPackageDeclaration().ifPresent(pd -> {
            classMeta.packageName = pd.getNameAsString();
        });
        classMeta.qualifiedName = classMeta.packageName.isEmpty() ? 
                classMeta.className : classMeta.packageName + "." + classMeta.className;

        // 收集导入包以做简单的类名解析
        Map<String, String> imports = new HashMap<>();
        cu.getImports().forEach(im -> {
            String name = im.getNameAsString();
            String simpleName = name.substring(name.lastIndexOf('.') + 1);
            imports.put(simpleName, name);
        });

        // 收集类注解
        for (AnnotationExpr ann : classDecl.getAnnotations()) {
            String name = ann.getNameAsString();
            String value = ann.toString(); // 包含属性
            classMeta.annotations.put(name, value);
        }

        // 收集成员变量
        for (FieldDeclaration field : classDecl.getFields()) {
            String typeStr = field.getElementType().asString();
            String fullType = imports.getOrDefault(typeStr, typeStr);
            // 如果导入中没有，且在同一个包下，通常就是当前包
            if (!fullType.contains(".") && !classMeta.packageName.isEmpty()) {
                fullType = classMeta.packageName + "." + fullType;
            }
            for (VariableDeclarator var : field.getVariables()) {
                classMeta.fieldTypes.put(var.getNameAsString(), fullType);
            }
        }

        // 收集方法
        for (MethodDeclaration method : classDecl.getMethods()) {
            JavaMethodMeta methodMeta = new JavaMethodMeta();
            methodMeta.name = method.getNameAsString();

            // 收集方法注解
            for (AnnotationExpr ann : method.getAnnotations()) {
                methodMeta.annotations.put(ann.getNameAsString(), ann.toString());
            }

            // 收集方法内部调用
            method.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    super.visit(n, arg);
                    String scope = n.getScope().map(Objects::toString).orElse("this");
                    String methodName = n.getNameAsString();
                    MethodCall call = new MethodCall(scope, methodName);
                    n.getArguments().forEach(a -> call.getArguments().add(a.toString()));
                    
                    // 基于成员变量推断被调用的类
                    if (classMeta.fieldTypes.containsKey(scope)) {
                        call.setResolvedClass(classMeta.fieldTypes.get(scope));
                    } else if (scope.equals("this")) {
                        call.setResolvedClass(classMeta.qualifiedName);
                    } else {
                        // 尝试在 imports 中匹配 scope （如有些静态方法调用：Utils.doSomething）
                        if (imports.containsKey(scope)) {
                            call.setResolvedClass(imports.get(scope));
                        }
                    }
                    methodMeta.methodCalls.add(call);
                }
            }, null);

            classMeta.methods.add(methodMeta);
        }

        return classMeta;
    }
}
