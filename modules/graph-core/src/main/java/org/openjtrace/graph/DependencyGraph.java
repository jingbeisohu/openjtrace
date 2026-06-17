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
     * 采用 BFS 保证在大规模循环依赖场景下的工业级稳定性，防止出现 StackOverflow 或 OOM。
     * 返回所有受影响路径的最短路径集合，每条路径表示为节点 ID 列表（上游 -> 下游）
     */
    public List<List<String>> findImpactedPaths(String startNodeId) {
        List<List<String>> result = new ArrayList<>();
        if (!nodes.containsKey(startNodeId)) {
            return result;
        }

        Queue<PathNode> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        // 初始化队列，包含变更目标点及其当前路径
        queue.add(new PathNode(startNodeId, new ArrayList<>(Collections.singletonList(startNodeId))));
        visited.add(startNodeId);

        while (!queue.isEmpty()) {
            PathNode current = queue.poll();
            String u = current.nodeId;
            List<String> path = current.path;

            List<Edge> inEdges = incomingEdges.getOrDefault(u, Collections.emptyList());
            // 如果没有更上游的调用，或者到达了链路最顶端，说明发现了一条完整的调用波及路径
            if (inEdges.isEmpty()) {
                List<String> finalPath = new ArrayList<>(path);
                // 倒序排列：将路径调整为 “上游调用者 -> ... -> 变更目标点” 顺序呈现
                Collections.reverse(finalPath);
                result.add(finalPath);
                continue;
            }

            boolean hasUnvisitedParent = false;
            for (Edge edge : inEdges) {
                String v = edge.getSourceId();
                if (!visited.contains(v)) {
                    visited.add(v);
                    hasUnvisitedParent = true;
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(v);
                    queue.add(new PathNode(v, newPath));
                }
            }

            // 如果所有的上游父节点都已经被全局 visited 访问过（遇到环路被剪枝拦截），
            // 则在此处终止，并将当前已有的局部链路作为受波及路径保存下来
            if (!hasUnvisitedParent) {
                List<String> finalPath = new ArrayList<>(path);
                Collections.reverse(finalPath);
                result.add(finalPath);
            }
        }

        return result;
    }

    private static class PathNode {
        String nodeId;
        List<String> path;

        PathNode(String nodeId, List<String> path) {
            this.nodeId = nodeId;
            this.path = path;
        }
    }
}
