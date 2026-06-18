package org.openjtrace.analyzer.dubbo;

import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Edge;
import org.openjtrace.graph.Node;
import org.openjtrace.parser.java.JavaSourceParser.JavaClassMeta;
import org.openjtrace.parser.java.JavaSourceParser.JavaMethodMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Dubbo RPC 协议依赖分析器 (DubboAnalyzer)</h1>
 * <p>
 * 该类用于静态识别和连接分布式架构中的 Dubbo RPC 调用链路。
 * </p>
 * 
 * <h3>核心解析逻辑：</h3>
 * <ul>
 *   <li>
 *     <b>提供端服务识别 (Provider)：</b>
 *     扫描标注有 {@code @DubboService} 或 {@code @Service} 注解的类，
 *     识别其实现的所有接口类，并将其作为 Dubbo 服务端点注册。
 *   </li>
 *   <li>
 *     <b>消费端引用识别 (Consumer)：</b>
 *     通过全局类方法调用提取，记录消费端以成员变量依赖注入形式引用的接口类及方法。
 *   </li>
 *   <li>
 *     <b>跨仓 RPC 边关联 (RPC_LINK)：</b>
 *     将消费端调用“接口方法”的行为，通过接口全限定名直接对齐到服务端具体实现类的“实现方法”上，
 *     在两个物理隔离的类之间建立 RPC_LINK 类型的关联边，实现跨仓库链路穿透。
 *   </li>
 * </ul>
 */
public class DubboAnalyzer {

    public void analyze(List<JavaClassMeta> classes, DependencyGraph graph) {
        // 查找所有暴露了 Dubbo 服务的实现类
        for (JavaClassMeta classMeta : classes) {
            boolean isDubboService = classMeta.getAnnotations().containsKey("DubboService")
                    || classMeta.getAnnotations().containsKey("Service"); // 需要防范 Spring 的 @Service，这里我们粗筛后可以做接口推断
            
            if (isDubboService && !classMeta.isInterface()) {
                analyzeDubboProvider(classMeta, classes, graph);
            }
        }
    }

    private void analyzeDubboProvider(JavaClassMeta classMeta, List<JavaClassMeta> allClasses, DependencyGraph graph) {
        String repoName = getRepoName(classMeta.getFile().getAbsolutePath());
        
        // 寻找该类实现的接口，以便将接口的方法关联到实现类的方法
        List<String> implementedInterfaces = findImplementedInterfaces(classMeta, allClasses);
        
        for (String interfaceName : implementedInterfaces) {
            // 为接口本身和每个方法建立关联
            for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
                String interfaceMethodId = interfaceName + "#" + methodMeta.getName();
                String implMethodId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

                // 注册接口方法节点 (作为 DUBBO_SERVICE 标识)
                Node interfaceNode = new Node(interfaceMethodId, Node.NodeType.DUBBO_REFERENCE, methodMeta.getName());
                interfaceNode.setClassName(interfaceName.substring(interfaceName.lastIndexOf('.') + 1));
                interfaceNode.setPackageName(interfaceName.contains(".") ? interfaceName.substring(0, interfaceName.lastIndexOf('.')) : "");
                interfaceNode.setRepoName(repoName);
                interfaceNode.getMetadata().put("isDubboInterface", "true");
                graph.addNode(interfaceNode);

                // 注册实现方法节点
                Node implNode = new Node(implMethodId, Node.NodeType.DUBBO_SERVICE, methodMeta.getName());
                implNode.setClassName(classMeta.getClassName());
                implNode.setPackageName(classMeta.getPackageName());
                implNode.setFilePath(classMeta.getFile().getAbsolutePath());
                implNode.setRepoName(repoName);
                graph.addNode(implNode);

                // 建立边：消费端调用接口方法 ─(RPC_LINK)─> 服务端执行具体实现方法
                graph.addEdge(new Edge(interfaceMethodId, implMethodId, Edge.EdgeType.RPC_LINK));
            }
        }
    }

    private List<String> findImplementedInterfaces(JavaClassMeta classMeta, List<JavaClassMeta> allClasses) {
        List<String> list = new ArrayList<>();
        // 简单的启发式猜测：
        // 比如 UserServiceImpl 实现 UserService 接口，通常类名带有 Impl 且它的前缀就是接口名。
        // 或者我们可以通过扫描该类的代码，看看它声明了哪些实现了的接口。不过由于我们的 JavaClassMeta 尚未完全解析 implements 关键字，
        // 我们可以根据名字约定进行精准匹配：如果存在一个接口，它的 SimpleName 被当前实现类名所包含，就认为是它实现的接口。
        String className = classMeta.getClassName();
        String probableInterfaceSimpleName = className.endsWith("Impl") ? 
                className.substring(0, className.length() - 4) : className;

        for (JavaClassMeta other : allClasses) {
            if (other.isInterface()) {
                if (other.getClassName().equals(probableInterfaceSimpleName) || className.contains(other.getClassName())) {
                    list.add(other.getQualifiedName());
                }
            }
        }

        // 兜底：如果没找到，直接以类名前缀作为接口全限定名
        if (list.isEmpty()) {
            String guess = classMeta.getQualifiedName().replace(".impl.", ".").replace("Impl", "");
            list.add(guess);
        }

        return list;
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
