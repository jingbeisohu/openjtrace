package org.openjtrace.graph;

import java.util.Objects;

/**
 * <h1>依赖图边关系模型 (Edge)</h1>
 * <p>
 * 该类定义了依赖图中两个节点（Node）之间的关联方向及类型。边是有向的，从源节点（sourceId）指向目标节点（targetId）。
 * </p>
 * 
 * <h3>核心关联边类型 (EdgeType)：</h3>
 * <ul>
 *   <li>{@link EdgeType#CALLS} - 方法间的静态调用关系，例如方法 A 调用了方法 B；或 Controller 端点指向其绑定的 Handler 方法</li>
 *   <li>{@link EdgeType#IMPLEMENTS} - Java 类与接口的实现关系，例如 UserServiceImpl 实现 UserService 接口</li>
 *   <li>{@link EdgeType#MAPS_TO} - Mapper 接口方法与 MyBatis XML SQL 语句的绑定映射关系</li>
 *   <li>{@link EdgeType#RPC_LINK} - 分布式调用链路中，消费端 (Reference) 跨服务指向提供端 (Service) 接口实现的关联关系</li>
 * </ul>
 */
public class Edge {
    public enum EdgeType {
        CALLS,          // e.g. Method A calls Method B
        IMPLEMENTS,     // e.g. Class implements Interface
        MAPS_TO,        // e.g. Mapper Method maps to MyBatis SQL
        RPC_LINK        // e.g. Dubbo Reference links to Dubbo Service
    }

    private String sourceId;
    private String targetId;
    private EdgeType type;

    public Edge() {}

    public Edge(String sourceId, String targetId, EdgeType type) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.type = type;
    }

    // Getters and Setters
    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public EdgeType getType() {
        return type;
    }

    public void setType(EdgeType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return Objects.equals(sourceId, edge.sourceId) &&
                Objects.equals(targetId, edge.targetId) &&
                type == edge.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceId, targetId, type);
    }

    @Override
    public String toString() {
        return "Edge{" +
                "sourceId='" + sourceId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", type=" + type +
                '}';
    }
}
