package org.openjtrace.analyzer.http;

import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Edge;
import org.openjtrace.graph.Node;
import org.openjtrace.parser.java.JavaSourceParser.JavaClassMeta;
import org.openjtrace.parser.java.JavaSourceParser.JavaMethodMeta;
import org.openjtrace.parser.java.JavaSourceParser.MethodCall;

import java.util.List;
import java.util.Map;

public class HttpAnalyzer {

    public void analyze(List<JavaClassMeta> classes, DependencyGraph graph) {
        for (JavaClassMeta classMeta : classes) {
            boolean isController = classMeta.getAnnotations().containsKey("RestController") 
                    || classMeta.getAnnotations().containsKey("Controller");
            boolean isFeign = classMeta.getAnnotations().containsKey("FeignClient");

            if (isController) {
                analyzeController(classMeta, graph);
            } else if (isFeign) {
                analyzeFeignClient(classMeta, graph);
            }

            // 另外，解析普通类的方法内部调用，如果是普通方法调用，我们为它们连接 CALLS 边
            analyzeMethodCalls(classMeta, graph);
        }
    }

    private void analyzeController(JavaClassMeta classMeta, DependencyGraph graph) {
        String classPrefix = getRequestMappingPath(classMeta.getAnnotations().get("RequestMapping"));

        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            String methodPath = "";
            String httpMethod = "GET"; // 默认 GET

            if (methodMeta.getAnnotations().containsKey("GetMapping")) {
                methodPath = getMappingPath(methodMeta.getAnnotations().get("GetMapping"));
                httpMethod = "GET";
            } else if (methodMeta.getAnnotations().containsKey("PostMapping")) {
                methodPath = getMappingPath(methodMeta.getAnnotations().get("PostMapping"));
                httpMethod = "POST";
            } else if (methodMeta.getAnnotations().containsKey("PutMapping")) {
                methodPath = getMappingPath(methodMeta.getAnnotations().get("PutMapping"));
                httpMethod = "PUT";
            } else if (methodMeta.getAnnotations().containsKey("DeleteMapping")) {
                methodPath = getMappingPath(methodMeta.getAnnotations().get("DeleteMapping"));
                httpMethod = "DELETE";
            } else if (methodMeta.getAnnotations().containsKey("RequestMapping")) {
                methodPath = getMappingPath(methodMeta.getAnnotations().get("RequestMapping"));
                httpMethod = getRequestMappingMethod(methodMeta.getAnnotations().get("RequestMapping"));
            } else {
                continue; // 不是 HTTP 接口方法
            }

            String fullPath = sanitizePath(classPrefix + "/" + methodPath);
            String apiId = "HTTP:" + httpMethod + ":" + fullPath;
            String methodNodeId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

            // 创建 HTTP 接口节点
            Node apiNode = new Node(apiId, Node.NodeType.HTTP_API, httpMethod + " " + fullPath);
            apiNode.setRepoName(getRepoName(classMeta.getFile().getAbsolutePath()));
            graph.addNode(apiNode);

            // 创建方法节点
            Node methodNode = new Node(methodNodeId, Node.NodeType.METHOD, methodMeta.getName());
            methodNode.setClassName(classMeta.getClassName());
            methodNode.setPackageName(classMeta.getPackageName());
            methodNode.setFilePath(classMeta.getFile().getAbsolutePath());
            methodNode.setRepoName(getRepoName(classMeta.getFile().getAbsolutePath()));
            graph.addNode(methodNode);

            // 建立边：API 调用 对应的 Java Controller 方法
            graph.addEdge(new Edge(apiId, methodNodeId, Edge.EdgeType.CALLS));
        }
    }

    private void analyzeFeignClient(JavaClassMeta classMeta, DependencyGraph graph) {
        // 通常 FeignClient 接口上可能有 RequestMapping
        String classPrefix = getRequestMappingPath(classMeta.getAnnotations().get("RequestMapping"));

        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            String methodPath = "";
            String httpMethod = "GET";

            if (methodMeta.getAnnotations().containsKey("GetMapping")) {
                methodPath = getMappingPath(methodMeta.getAnnotations().get("GetMapping"));
                httpMethod = "GET";
            } else if (methodMeta.getAnnotations().containsKey("PostMapping")) {
                methodPath = getMappingPath(methodMeta.getAnnotations().get("PostMapping"));
                httpMethod = "POST";
            } else if (methodMeta.getAnnotations().containsKey("RequestMapping")) {
                methodPath = getMappingPath(methodMeta.getAnnotations().get("RequestMapping"));
                httpMethod = getRequestMappingMethod(methodMeta.getAnnotations().get("RequestMapping"));
            } else {
                continue;
            }

            String fullPath = sanitizePath(classPrefix + "/" + methodPath);
            String apiId = "HTTP:" + httpMethod + ":" + fullPath;
            String feignMethodId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

            // 创建 HTTP 接口节点
            Node apiNode = new Node(apiId, Node.NodeType.HTTP_API, httpMethod + " " + fullPath);
            apiNode.setRepoName(getRepoName(classMeta.getFile().getAbsolutePath()));
            graph.addNode(apiNode);

            // 创建 Feign 方法节点
            Node methodNode = new Node(feignMethodId, Node.NodeType.METHOD, methodMeta.getName());
            methodNode.setClassName(classMeta.getClassName());
            methodNode.setPackageName(classMeta.getPackageName());
            methodNode.setFilePath(classMeta.getFile().getAbsolutePath());
            methodNode.setRepoName(getRepoName(classMeta.getFile().getAbsolutePath()));
            graph.addNode(methodNode);

            // 建立边：FeignClient 方法 远程调用该 HTTP API
            graph.addEdge(new Edge(feignMethodId, apiId, Edge.EdgeType.CALLS));
        }
    }

    private void analyzeMethodCalls(JavaClassMeta classMeta, DependencyGraph graph) {
        String repoName = getRepoName(classMeta.getFile().getAbsolutePath());
        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            String sourceId = classMeta.getQualifiedName() + "#" + methodMeta.getName();
            
            // 确保当前方法节点被添加
            Node sourceNode = new Node(sourceId, Node.NodeType.METHOD, methodMeta.getName());
            sourceNode.setClassName(classMeta.getClassName());
            sourceNode.setPackageName(classMeta.getPackageName());
            sourceNode.setFilePath(classMeta.getFile().getAbsolutePath());
            sourceNode.setRepoName(repoName);
            graph.addNode(sourceNode);

            for (MethodCall call : methodMeta.getMethodCalls()) {
                if (call.getResolvedClass() != null) {
                    String targetId = call.getResolvedClass() + "#" + call.getName();
                    // 这里我们尝试将方法调用关联起来，暂时为 target 建立一个占位节点，等扫描到真实类时再补充属性
                    Node targetNode = new Node(targetId, Node.NodeType.METHOD, call.getName());
                    graph.addNode(targetNode);

                    graph.addEdge(new Edge(sourceId, targetId, Edge.EdgeType.CALLS));
                }
            }
        }
    }

    private String getRequestMappingPath(String annotationStr) {
        if (annotationStr == null) return "";
        return extractValueFromAnnotation(annotationStr);
    }

    private String getMappingPath(String annotationStr) {
        if (annotationStr == null) return "";
        return extractValueFromAnnotation(annotationStr);
    }

    private String getRequestMappingMethod(String annotationStr) {
        if (annotationStr == null) return "GET";
        if (annotationStr.contains("POST")) return "POST";
        if (annotationStr.contains("PUT")) return "PUT";
        if (annotationStr.contains("DELETE")) return "DELETE";
        return "GET";
    }

    private String extractValueFromAnnotation(String annotationStr) {
        // 极简静态解析注解中的路径值。支持:
        // @GetMapping("/user") -> /user
        // @RequestMapping(value = "/user") -> /user
        // @RequestMapping(path = "/user") -> /user
        int valueIndex = annotationStr.indexOf("value");
        if (valueIndex == -1) {
            valueIndex = annotationStr.indexOf("path");
        }
        
        if (valueIndex != -1) {
            int start = annotationStr.indexOf("\"", valueIndex);
            if (start != -1) {
                int end = annotationStr.indexOf("\"", start + 1);
                if (end != -1) {
                    return annotationStr.substring(start + 1, end);
                }
            }
        } else {
            // 可能是直接传值，如 @GetMapping("/user")
            int start = annotationStr.indexOf("\"");
            if (start != -1) {
                int end = annotationStr.indexOf("\"", start + 1);
                if (end != -1) {
                    return annotationStr.substring(start + 1, end);
                }
            }
        }
        return "";
    }

    private String sanitizePath(String path) {
        String clean = path.replace("//", "/");
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (!clean.startsWith("/")) {
            clean = "/" + clean;
        }
        return clean;
    }

    private String getRepoName(String absolutePath) {
        // 基于路径获取仓库目录名称
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
