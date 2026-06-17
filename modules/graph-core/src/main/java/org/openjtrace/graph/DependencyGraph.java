package org.openjtrace.graph;

import java.util.*;

public class DependencyGraph {
    private Map<String, Node> nodes = new HashMap<>();
    private Set<Edge> edges = new HashSet<>();

    // Adjacency lists
    private Map<String, List<Edge>> outgoingEdges = new HashMap<>();
    private Map<String, List<Edge>> incomingEdges = new HashMap<>();

    public void addNode(Node node) {
        if (node != null && node.getId() != null) {
            nodes.putIfAbsent(node.getId(), node);
        }
    }

    public Node getNode(String id) {
        return nodes.get(id);
    }

    public Map<String, Node> getNodes() {
        return nodes;
    }

    public void addEdge(Edge edge) {
        if (edge == null || edge.getSourceId() == null || edge.getTargetId() == null) {
            return;
        }
        if (edges.add(edge)) {
            outgoingEdges.computeIfAbsent(edge.getSourceId(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.getTargetId(), k -> new ArrayList<>()).add(edge);
        }
    }

    public Set<Edge> getEdges() {
        return edges;
    }

    public Map<String, List<Edge>> getOutgoingEdges() {
        return outgoingEdges;
    }

    public Map<String, List<Edge>> getIncomingEdges() {
        return incomingEdges;
    }

    /**
     * 逆向追踪受影响的路径（从变更点出发，沿着入边反向寻找上游源头，比如 Controller 或 RPC 暴露接口）
     * 返回所有受影响路径的集合，每条路径表示为节点 ID 列表（上游 -> 下游）
     */
    public List<List<String>> findImpactedPaths(String startNodeId) {
        List<List<String>> result = new ArrayList<>();
        if (!nodes.containsKey(startNodeId)) {
            return result;
        }
        
        Set<String> visited = new HashSet<>();
        List<String> currentPath = new ArrayList<>();
        currentPath.add(startNodeId);
        
        dfsReverse(startNodeId, visited, currentPath, result);
        return result;
    }

    private void dfsReverse(String u, Set<String> visited, List<String> currentPath, List<List<String>> result) {
        List<Edge> inEdges = incomingEdges.getOrDefault(u, Collections.emptyList());
        
        // 如果没有更上游的调用者，说明到达了一个链条的顶端（如 Controller / Dubbo Service）
        if (inEdges.isEmpty()) {
            List<String> path = new ArrayList<>(currentPath);
            // 将路径倒序，使其看起来是从上游调用者指向下游变更源
            Collections.reverse(path);
            result.add(path);
            return;
        }

        visited.add(u);
        boolean extended = false;
        for (Edge edge : inEdges) {
            String v = edge.getSourceId();
            if (!visited.contains(v)) {
                extended = true;
                currentPath.add(v);
                dfsReverse(v, visited, currentPath, result);
                currentPath.remove(currentPath.size() - 1);
            }
        }
        
        // 如果因为环路导致无法继续反向延伸，也保存当前路径
        if (!extended) {
            List<String> path = new ArrayList<>(currentPath);
            Collections.reverse(path);
            result.add(path);
        }
        visited.remove(u);
    }
}
