package org.openjtrace.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * <h1>依赖图节点模型 (Node)</h1>
 * <p>
 * 该类代表静态分析拓扑图中的实体节点。每个节点对应 Java 项目中的一个具体实体（如方法、HTTP接口、RPC服务或SQL语句）。
 * </p>
 * 
 * <h3>核心节点类型 (NodeType)：</h3>
 * <ul>
 *   <li>{@link NodeType#METHOD} - 普通的 Java 类方法实现</li>
 *   <li>{@link NodeType#HTTP_API} - Spring Boot Controller 暴露的 HTTP 路由端点或 Feign 远程调用接口</li>
 *   <li>{@link NodeType#DUBBO_SERVICE} - Dubbo 提供端 (Provider) 发布的具体服务实现方法</li>
 *   <li>{@link NodeType#DUBBO_REFERENCE} - Dubbo 消费端 (Consumer) 引入的 RPC 接口定义方法</li>
 *   <li>{@link NodeType#SQL_QUERY} - MyBatis XML 映射文件中声明的具体 SQL 语句节点 (如 select/insert/update/delete)</li>
 *   <li>{@link NodeType#MQ_TOPIC} - 异步消息队列 (RocketMQ/Kafka/RabbitMQ) 的消息主题或队列端点</li>
 * </ul>
 */
public class Node {
    public enum NodeType {
        METHOD,
        HTTP_API,
        DUBBO_SERVICE,
        DUBBO_REFERENCE,
        SQL_QUERY,
        MQ_TOPIC
    }

    private String id;
    private NodeType type;
    private String name; // e.g. methodName or API path
    private String packageName;
    private String className;
    private String filePath;
    private String repoName;
    private Map<String, String> metadata = new HashMap<>();

    public Node() {}

    public Node(String id, NodeType type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(id, node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Node{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", name='" + name + '\'' +
                '}';
    }
}
