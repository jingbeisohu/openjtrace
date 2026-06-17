package org.openjtrace.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
