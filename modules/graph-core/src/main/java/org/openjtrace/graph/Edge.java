package org.openjtrace.graph;

import java.util.Objects;

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
