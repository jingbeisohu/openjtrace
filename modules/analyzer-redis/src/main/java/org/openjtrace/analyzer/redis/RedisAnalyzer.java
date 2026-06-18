package org.openjtrace.analyzer.redis;

import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Edge;
import org.openjtrace.graph.Node;
import org.openjtrace.parser.java.JavaSourceParser.JavaClassMeta;
import org.openjtrace.parser.java.JavaSourceParser.JavaMethodMeta;
import org.openjtrace.parser.java.JavaSourceParser.MethodCall;

import java.util.List;

/**
 * <h1>Redis 缓存依赖分析器 (RedisAnalyzer)</h1>
 * <p>
 * 该类静态分析 Java 源码中关于 Redis 缓存的使用。
 * </p>
 * 
 * <h3>解析流与规则：</h3>
 * <ul>
 *   <li>
 *     <b>Spring Cache 缓存注解解析：</b>
 *     识别类或方法上的 {@code @Cacheable}、{@code @CacheEvict} 和 {@code @CachePut}。
 *     静态提取注解中的 {@code value} 或 {@code cacheNames} 属性作为 Redis 缓存分区名，
 *     在依赖图上将其建模为 {@link Node.NodeType#REDIS_CACHE} 节点。
 *   </li>
 *   <li>
 *     <b>RedisTemplate 模板调用解析：</b>
 *     扫描方法体中的 {@link MethodCall}，如果 scope 变量包含 {@code redisTemplate} 或 {@code stringRedisTemplate}：
 *     提取出调用操作的 Key 参数前缀（如果是字符串字面量，如 {@code "user:session:"}），并在图中建立关联边。
 *   </li>
 * </ul>
 */
public class RedisAnalyzer {

    public void analyze(List<JavaClassMeta> classes, DependencyGraph graph) {
        for (JavaClassMeta classMeta : classes) {
            analyzeSpringCache(classMeta, graph);
            analyzeRedisTemplate(classMeta, graph);
        }
    }

    private void analyzeSpringCache(JavaClassMeta classMeta, DependencyGraph graph) {
        String repoName = getRepoName(classMeta.getFile().getAbsolutePath());
        
        // 类级别的缓存注解（若有，作为默认分区名）
        String classCacheName = getSpringCacheName(classMeta.getAnnotations().get("CacheConfig"));

        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            String cacheName = "";
            if (methodMeta.getAnnotations().containsKey("Cacheable")) {
                cacheName = getSpringCacheName(methodMeta.getAnnotations().get("Cacheable"));
            } else if (methodMeta.getAnnotations().containsKey("CachePut")) {
                cacheName = getSpringCacheName(methodMeta.getAnnotations().get("CachePut"));
            } else if (methodMeta.getAnnotations().containsKey("CacheEvict")) {
                cacheName = getSpringCacheName(methodMeta.getAnnotations().get("CacheEvict"));
            }

            if (cacheName.isEmpty()) {
                cacheName = classCacheName;
            }

            if (!cacheName.isEmpty()) {
                linkRedisCacheToMethod(cacheName, classMeta, methodMeta, repoName, graph);
            }
        }
    }

    private void linkRedisCacheToMethod(String cacheName, JavaClassMeta classMeta, JavaMethodMeta methodMeta, String repoName, DependencyGraph graph) {
        String redisNodeId = "REDIS:" + cacheName;
        String methodNodeId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

        // 注册 Redis 缓存节点
        Node redisNode = new Node(redisNodeId, Node.NodeType.REDIS_CACHE, "Redis Cache: " + cacheName);
        redisNode.setRepoName(repoName);
        graph.addNode(redisNode);

        // 注册方法节点
        Node methodNode = new Node(methodNodeId, Node.NodeType.METHOD, methodMeta.getName());
        methodNode.setClassName(classMeta.getClassName());
        methodNode.setPackageName(classMeta.getPackageName());
        methodNode.setFilePath(classMeta.getFile().getAbsolutePath());
        methodNode.setRepoName(repoName);
        graph.addNode(methodNode);

        // 建立边：方法 ➜ 依赖/操作对应的 Redis 缓存
        graph.addEdge(new Edge(methodNodeId, redisNodeId, Edge.EdgeType.CALLS));
    }

    private void analyzeRedisTemplate(JavaClassMeta classMeta, DependencyGraph graph) {
        String repoName = getRepoName(classMeta.getFile().getAbsolutePath());

        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            String sourceMethodId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

            for (MethodCall call : methodMeta.getMethodCalls()) {
                boolean isRedisTemplate = call.getScope().toLowerCase().contains("redistemplate");
                boolean isOps = call.getName().startsWith("opsFor");

                // 如果是直接调用 template.delete(key) 或者 opsForValue().set(key, ...)
                if (isRedisTemplate && !call.getArguments().isEmpty()) {
                    String rawKey = call.getArguments().get(0);
                    String keyPrefix = cleanLiteral(rawKey);
                    if (!keyPrefix.isEmpty()) {
                        registerRedisCall(keyPrefix, classMeta, methodMeta, sourceMethodId, repoName, graph);
                    }
                }
            }
        }
    }

    private void registerRedisCall(String keyPrefix, JavaClassMeta classMeta, JavaMethodMeta methodMeta, String sourceMethodId, String repoName, DependencyGraph graph) {
        // 缩短多级的 Redis Key 前缀作为缓存段标识
        String cacheName = keyPrefix.endsWith(":") ? keyPrefix.substring(0, keyPrefix.length() - 1) : keyPrefix;
        String redisNodeId = "REDIS:" + cacheName;

        Node redisNode = new Node(redisNodeId, Node.NodeType.REDIS_CACHE, "Redis Key: " + cacheName);
        redisNode.setRepoName(repoName);
        graph.addNode(redisNode);

        Node senderNode = new Node(sourceMethodId, Node.NodeType.METHOD, methodMeta.getName());
        senderNode.setClassName(classMeta.getClassName());
        senderNode.setPackageName(classMeta.getPackageName());
        senderNode.setFilePath(classMeta.getFile().getAbsolutePath());
        senderNode.setRepoName(repoName);
        graph.addNode(senderNode);

        graph.addEdge(new Edge(sourceMethodId, redisNodeId, Edge.EdgeType.CALLS));
    }

    private String getSpringCacheName(String annotationStr) {
        if (annotationStr == null) return "";
        // 静态匹配 @Cacheable(value = "user", ...) 中的 value 属性
        int valIndex = annotationStr.indexOf("value");
        if (valIndex == -1) {
            valIndex = annotationStr.indexOf("cacheNames");
        }
        if (valIndex != -1) {
            int start = annotationStr.indexOf("\"", valIndex);
            if (start != -1) {
                int end = annotationStr.indexOf("\"", start + 1);
                if (end != -1) {
                    return annotationStr.substring(start + 1, end);
                }
            }
        } else {
            // 直接传值的情况，如 @Cacheable("user")
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

    private String cleanLiteral(String expr) {
        if (expr == null) return "";
        String clean = expr.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"")) {
            return clean.substring(1, clean.length() - 1);
        }
        return "";
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
