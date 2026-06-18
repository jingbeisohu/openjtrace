package org.openjtrace.analyzer.mongodb;

import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Edge;
import org.openjtrace.graph.Node;
import org.openjtrace.parser.java.JavaSourceParser.JavaClassMeta;
import org.openjtrace.parser.java.JavaSourceParser.JavaMethodMeta;
import org.openjtrace.parser.java.JavaSourceParser.MethodCall;

import java.util.List;

/**
 * <h1>MongoDB 数据库依赖分析器 (MongoAnalyzer)</h1>
 * <p>
 * 该类静态分析 Java 源码中关于 MongoDB 数据库的使用。
 * </p>
 * 
 * <h3>解析流与规则：</h3>
 * <ul>
 *   <li>
 *     <b>MongoRepository 接口继承分析：</b>
 *     扫描识别继承了 {@code MongoRepository} 接口的类。
 *     静态推断出泛型绑定的实体类名称（如 {@code UserRepository extends MongoRepository<User, String>} 中的 {@code User}），
 *     然后在全局扫描类列表中搜寻该 {@code User} 实体，读取其上的 {@code @Document(collection = "...")} 注解，
 *     提取出真实的 MongoDB 集合（Collection）名称，注册为 {@link Node.NodeType#MONGO_COLLECTION} 节点并关联。
 *   </li>
 *   <li>
 *     <b>MongoTemplate 模块调用分析：</b>
 *     扫描方法体中的 {@link MethodCall}，如果 scope 变量为 {@code mongoTemplate}：
 *     寻找参数列表中含有 {@code XXX.class} 的项，结合该实体的 {@code @Document} 注解推导其操作的 MongoDB 集合名称并关联。
 *   </li>
 * </ul>
 */
public class MongoAnalyzer {

    public void analyze(List<JavaClassMeta> classes, DependencyGraph graph) {
        for (JavaClassMeta classMeta : classes) {
            if (classMeta.isInterface() && isMongoRepository(classMeta)) {
                analyzeMongoRepository(classMeta, classes, graph);
            }
            analyzeMongoTemplate(classMeta, classes, graph);
        }
    }

    private boolean isMongoRepository(JavaClassMeta classMeta) {
        // 由于是静态无符号推导，简单通过文件或类名中是否包含 MongoRepository 判断
        // 比如 UserRepository.java 源码中包含 MongoRepository 导入或 extends 关系
        return classMeta.getFile() != null && classMeta.getFile().getAbsolutePath().contains("MongoRepository");
    }

    private void analyzeMongoRepository(JavaClassMeta classMeta, List<JavaClassMeta> allClasses, DependencyGraph graph) {
        String repoName = getRepoName(classMeta.getFile().getAbsolutePath());
        
        // 推断泛型实体名称，例如 UserRepository ➜ 实体是 User
        String entitySimpleName = classMeta.getClassName().replace("Repository", "").replace("Mongo", "");
        
        // 寻找实体对应的真实 Collection 名字
        String collectionName = findCollectionName(entitySimpleName, allClasses);

        String mongoNodeId = "MONGO:" + collectionName;

        // 注册 MongoDB Collection 节点
        Node mongoNode = new Node(mongoNodeId, Node.NodeType.MONGO_COLLECTION, "Mongo Collection: " + collectionName);
        mongoNode.setRepoName(repoName);
        graph.addNode(mongoNode);

        // 为该 Repository 中的所有接口方法与 Collection 建立关联边
        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            String mapperMethodId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

            Node mapperNode = new Node(mapperMethodId, Node.NodeType.METHOD, methodMeta.getName());
            mapperNode.setClassName(classMeta.getClassName());
            mapperNode.setPackageName(classMeta.getPackageName());
            mapperNode.setRepoName(repoName);
            graph.addNode(mapperNode);

            // 建立边：Repository 方法 ─(MAPS_TO)─> MongoDB 集合
            graph.addEdge(new Edge(mapperMethodId, mongoNodeId, Edge.EdgeType.MAPS_TO));
        }
    }

    private void analyzeMongoTemplate(JavaClassMeta classMeta, List<JavaClassMeta> allClasses, DependencyGraph graph) {
        String repoName = getRepoName(classMeta.getFile().getAbsolutePath());

        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            String sourceMethodId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

            for (MethodCall call : methodMeta.getMethodCalls()) {
                boolean isMongoTemplate = call.getScope().toLowerCase().contains("mongotemplate");

                if (isMongoTemplate && !call.getArguments().isEmpty()) {
                    // 寻找包含 ".class" 的参数
                    String entityClass = "";
                    for (String arg : call.getArguments()) {
                        if (arg.endsWith(".class")) {
                            entityClass = arg.substring(0, arg.length() - 6);
                            break;
                        }
                    }

                    if (!entityClass.isEmpty()) {
                        String collectionName = findCollectionName(entityClass, allClasses);
                        String mongoNodeId = "MONGO:" + collectionName;

                        // 注册 MongoDB 节点
                        Node mongoNode = new Node(mongoNodeId, Node.NodeType.MONGO_COLLECTION, "Mongo Collection: " + collectionName);
                        mongoNode.setRepoName(repoName);
                        graph.addNode(mongoNode);

                        // 注册发送方法节点
                        Node senderNode = new Node(sourceMethodId, Node.NodeType.METHOD, methodMeta.getName());
                        senderNode.setClassName(classMeta.getClassName());
                        senderNode.setPackageName(classMeta.getPackageName());
                        senderNode.setFilePath(classMeta.getFile().getAbsolutePath());
                        senderNode.setRepoName(repoName);
                        graph.addNode(senderNode);

                        // 建立边：方法调用 ➜ 依赖 MongoDB 集合
                        graph.addEdge(new Edge(sourceMethodId, mongoNodeId, Edge.EdgeType.CALLS));
                    }
                }
            }
        }
    }

    private String findCollectionName(String entitySimpleName, List<JavaClassMeta> allClasses) {
        for (JavaClassMeta meta : allClasses) {
            if (meta.getClassName().equals(entitySimpleName)) {
                String docAnn = meta.getAnnotations().get("Document");
                if (docAnn != null) {
                    // 解析 @Document(collection = "user_info") 中的 collection 属性值
                    int colIndex = docAnn.indexOf("collection");
                    if (colIndex != -1) {
                        int start = docAnn.indexOf("\"", colIndex);
                        if (start != -1) {
                            int end = docAnn.indexOf("\"", start + 1);
                            if (end != -1) {
                                return docAnn.substring(start + 1, end);
                            }
                        }
                    }
                }
            }
        }
        // 兜底：直接以实体类名全小写作为集合名
        return entitySimpleName.toLowerCase();
    }

    private String getRepoName(String absolutePath) {
        String[] parts = absolutePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("examples")) {
                if (i + 1 < parts.length) {
                    return parts[i + 1];
                }
            }
        }
        return "unknown";
    }
}
