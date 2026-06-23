package org.openjtrace.cli;

import org.junit.jupiter.api.Test;
import org.openjtrace.analyzer.http.HttpAnalyzer;
import org.openjtrace.analyzer.redis.RedisAnalyzer;
import org.openjtrace.analyzer.mongodb.MongoAnalyzer;
import org.openjtrace.parser.java.JavaSourceParser;
import org.openjtrace.parser.java.JavaSourceParser.JavaClassMeta;
import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Node;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OpenJTraceCliTest {

    @Test
    public void testCliIntegrationScan() throws Exception {
        // examples 目录在项目根目录下
        // 运行测试时，当前工作目录通常是 cli 子模块的目录，因此 examples 的相对路径为 ../../examples
        File examplesDir = new File("../../examples");
        if (!examplesDir.exists()) {
            // 兜底：如果运行根目录的测试，可能相对路径就是 ./examples
            examplesDir = new File("examples");
        }
        
        assertTrue(examplesDir.exists(), "Examples 目录应当存在于工作区中");

        File tempReport = File.createTempFile("openjtrace-report-test", ".html");
        tempReport.deleteOnExit();

        // 实例化 CLI 并模拟调用参数
        OpenJTraceCli cli = new OpenJTraceCli();
        
        // 利用反射或直接设置参数（因 JCommander 参数为 private 字段，可以通过命令行参数的形式或者在测试里反射赋值）
        // 这里我们可以通过命令行参数形式初始化
        String[] args = {
            "-dirs", examplesDir.getAbsolutePath(),
            "-target", "SQL:org.openjtrace.example.mybatis.UserMapper#selectById",
            "-output", tempReport.getAbsolutePath()
        };

        // 运行 Cli
        OpenJTraceCli.main(args);

        // 验证报告文件是否生成且不为空
        assertTrue(tempReport.exists(), "HTML 报告文件应该被成功创建");
        assertTrue(tempReport.length() > 0, "生成的报告文件不能是空文件");
    }

    @Test
    public void testRedisAnalyzerIntegration() throws Exception {
        File examplesDir = new File("../../examples");
        if (!examplesDir.exists()) {
            examplesDir = new File("examples");
        }
        assertTrue(examplesDir.exists(), "Examples 目录应当存在");

        JavaSourceParser javaParser = new JavaSourceParser();
        List<JavaClassMeta> classMetas = javaParser.parseDirectory(examplesDir);
        
        DependencyGraph graph = new DependencyGraph();

        // 方法关联需要 HttpAnalyzer 中的 analyzeMethodCalls 进行基础方法图节点与调用边的搭建
        HttpAnalyzer httpAnalyzer = new HttpAnalyzer();
        httpAnalyzer.analyze(classMetas, graph);

        RedisAnalyzer redisAnalyzer = new RedisAnalyzer();
        redisAnalyzer.analyze(classMetas, graph);

        // 1. 验证注解缓存 Redis 节点
        String cacheNodeId = "REDIS:user-cache";
        Node cacheNode = graph.getNode(cacheNodeId);
        assertNotNull(cacheNode, "应能解析出 @Cacheable 对应的 Redis Cache 节点");
        assertEquals(Node.NodeType.REDIS_CACHE, cacheNode.getType());

        // 2. 验证 opsForValue() 调用缓存 Redis 节点
        String keyNodeId = "REDIS:user:session";
        Node keyNode = graph.getNode(keyNodeId);
        assertNotNull(keyNode, "应能解析出 redisTemplate 对应的 Redis Key 节点");
        assertEquals(Node.NodeType.REDIS_CACHE, keyNode.getType());

        // 3. 验证依赖边
        String getUserCachedMethod = "org.openjtrace.example.mybatis.UserCacheService#getUserCached";
        assertTrue(graph.getEdges().stream().anyMatch(e -> 
            e.getSourceId().equals(getUserCachedMethod) && e.getTargetId().equals(cacheNodeId)
        ), "应建立 getUserCached 到 Redis 缓存的连接边");

        String updateSessionMethod = "org.openjtrace.example.mybatis.UserCacheService#updateSession";
        assertTrue(graph.getEdges().stream().anyMatch(e -> 
            e.getSourceId().equals(updateSessionMethod) && e.getTargetId().equals(keyNodeId)
        ), "应建立 updateSession 到 Redis 缓存的连接边");
    }

    @Test
    public void testMongoAnalyzerIntegration() throws Exception {
        File examplesDir = new File("../../examples");
        if (!examplesDir.exists()) {
            examplesDir = new File("examples");
        }
        assertTrue(examplesDir.exists(), "Examples 目录应当存在");

        JavaSourceParser javaParser = new JavaSourceParser();
        List<JavaClassMeta> classMetas = javaParser.parseDirectory(examplesDir);
        
        DependencyGraph graph = new DependencyGraph();

        HttpAnalyzer httpAnalyzer = new HttpAnalyzer();
        httpAnalyzer.analyze(classMetas, graph);

        MongoAnalyzer mongoAnalyzer = new MongoAnalyzer();
        mongoAnalyzer.analyze(classMetas, graph);

        // 1. 验证 Mongo 实体及集合节点
        String mongoCollectionNodeId = "MONGO:user_info";
        Node collectionNode = graph.getNode(mongoCollectionNodeId);
        assertNotNull(collectionNode, "应能解析出 Mongo Collection 节点");
        assertEquals(Node.NodeType.MONGO_COLLECTION, collectionNode.getType());

        // 2. 验证 Repository 接口方法与 Collection 映射
        String repoMethodId = "org.openjtrace.example.mybatis.UserMongoRepository#findByName";
        assertTrue(graph.getEdges().stream().anyMatch(e -> 
            e.getSourceId().equals(repoMethodId) && e.getTargetId().equals(mongoCollectionNodeId)
        ), "应该建立 Repository 接口方法到 Mongo Collection 的 MAPS_TO 关联边");

        // 3. 验证 MongoTemplate 显式调用与 Collection 关联
        String removeUserMethod = "org.openjtrace.example.mybatis.UserMongoService#removeUser";
        assertTrue(graph.getEdges().stream().anyMatch(e -> 
            e.getSourceId().equals(removeUserMethod) && e.getTargetId().equals(mongoCollectionNodeId)
        ), "应该建立 MongoTemplate 显式调用到 Mongo Collection 的 CALLS 关联边");

        // 4. 验证完整链路追踪：Service 方法 到 Mongo Collection (通过 Repository)
        String queryUserMethod = "org.openjtrace.example.mybatis.UserMongoService#queryUser";
        // 期望：queryUser() -> findByName() -> MONGO:user_info
        List<List<String>> paths = graph.findImpactedPaths(mongoCollectionNodeId);
        assertFalse(paths.isEmpty(), "应该能逆向发现到 MongoDB Collection 的影响链");

        boolean linkFound = false;
        for (List<String> path : paths) {
            if (path.contains(queryUserMethod) && path.contains(repoMethodId) && path.contains(mongoCollectionNodeId)) {
                linkFound = true;
                break;
            }
        }
        assertTrue(linkFound, "应该能逆向发现从 Service 经过 Repository 接口到达 Mongo 集合的影响链");
    }

    @Test
    public void testMyBatisXmlParser() throws Exception {
        File examplesDir = new File("../../examples");
        if (!examplesDir.exists()) {
            examplesDir = new File("examples");
        }
        File xmlFile = new File(examplesDir, "mybatis-demo/src/main/resources/mapper/UserMapper.xml");
        assertTrue(xmlFile.exists(), "UserMapper.xml 应该存在于工作区中");

        MyBatisXmlParser.MyBatisXmlMeta meta = MyBatisXmlParser.parseMeta(xmlFile);
        assertEquals("org.openjtrace.example.mybatis.UserMapper", meta.getNamespace());
        assertFalse(meta.getSqlLocations().isEmpty(), "解析出的 SQL 位置不应为空");

        for (MyBatisXmlParser.SqlLocation loc : meta.getSqlLocations()) {
            assertNotNull(loc.getId());
            assertTrue(loc.getStartLine() > 0);
            assertTrue(loc.getEndLine() >= loc.getStartLine());
        }
    }

    @Test
    public void testGitDiffResolver() {
        File examplesDir = new File("../../examples");
        if (!examplesDir.exists()) {
            examplesDir = new File("examples");
        }
        File repoRoot = examplesDir.getParentFile();
        List<GitDiffResolver.FileDiff> diffs = GitDiffResolver.resolveDiff(List.of(repoRoot), "HEAD");
        assertNotNull(diffs);
        System.out.println("GitDiffResolver parsed diffs size: " + diffs.size());
    }
}
