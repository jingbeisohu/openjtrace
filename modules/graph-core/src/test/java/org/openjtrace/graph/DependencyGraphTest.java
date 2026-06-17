package org.openjtrace.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class DependencyGraphTest {

    @Test
    public void testGraphConstructionAndReverseDfs() {
        DependencyGraph graph = new DependencyGraph();

        // 1. 创建节点
        Node controllerNode = new Node("GET:/user", Node.NodeType.HTTP_API, "/user");
        Node serviceNode = new Node("UserService#getUser", Node.NodeType.METHOD, "getUser");
        Node mapperNode = new Node("UserMapper#selectById", Node.NodeType.METHOD, "selectById");
        Node sqlNode = new Node("SQL:select_user", Node.NodeType.SQL_QUERY, "select_user");

        graph.addNode(controllerNode);
        graph.addNode(serviceNode);
        graph.addNode(mapperNode);
        graph.addNode(sqlNode);

        // 2. 添加依赖边 (A -> B 代表 A 依赖 B / A 调用 B)
        graph.addEdge(new Edge("GET:/user", "UserService#getUser", Edge.EdgeType.CALLS));
        graph.addEdge(new Edge("UserService#getUser", "UserMapper#selectById", Edge.EdgeType.CALLS));
        graph.addEdge(new Edge("UserMapper#selectById", "SQL:select_user", Edge.EdgeType.MAPS_TO));

        // 3. 从变更点 SQL:select_user 开始逆向查找受波及路径
        List<List<String>> paths = graph.findImpactedPaths("SQL:select_user");

        // 预期有一条完整的链路，从 GET:/user 开始到达 SQL:select_user
        assertEquals(1, paths.size());
        List<String> path = paths.get(0);
        assertEquals(4, path.size());
        assertEquals("GET:/user", path.get(0));
        assertEquals("UserService#getUser", path.get(1));
        assertEquals("UserMapper#selectById", path.get(2));
        assertEquals("SQL:select_user", path.get(3));
    }

    @Test
    public void testMqDependencyLink() {
        DependencyGraph graph = new DependencyGraph();

        Node senderNode = new Node("OrderPayServiceImpl#payOrder", Node.NodeType.METHOD, "payOrder");
        Node mqNode = new Node("MQ:payment-topic", Node.NodeType.MQ_TOPIC, "MQ Topic: payment-topic");
        Node consumerNode = new Node("PaymentListener#onMessage", Node.NodeType.METHOD, "onMessage");

        graph.addNode(senderNode);
        graph.addNode(mqNode);
        graph.addNode(consumerNode);

        // 发送端发送消息到 Topic
        graph.addEdge(new Edge("OrderPayServiceImpl#payOrder", "MQ:payment-topic", Edge.EdgeType.CALLS));
        // 消息主题触发消费端
        graph.addEdge(new Edge("MQ:payment-topic", "PaymentListener#onMessage", Edge.EdgeType.CALLS));

        // 如果消费端的逻辑发生变动，逆向追踪波及的发送端
        List<List<String>> paths = graph.findImpactedPaths("PaymentListener#onMessage");

        assertEquals(1, paths.size());
        List<String> path = paths.get(0);
        assertEquals(3, path.size());
        assertEquals("OrderPayServiceImpl#payOrder", path.get(0));
        assertEquals("MQ:payment-topic", path.get(1));
        assertEquals("PaymentListener#onMessage", path.get(2));
    }

    @Test
    public void testCircularDependencyLoop() {
        DependencyGraph graph = new DependencyGraph();

        // 构造一个 A <-> B 的互相调用环路，并且外部由 C 触发调用了 A
        Node nodeA = new Node("A", Node.NodeType.METHOD, "methodA");
        Node nodeB = new Node("B", Node.NodeType.METHOD, "methodB");
        Node nodeC = new Node("C", Node.NodeType.METHOD, "methodC");

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        graph.addEdge(new Edge("C", "A", Edge.EdgeType.CALLS));
        graph.addEdge(new Edge("A", "B", Edge.EdgeType.CALLS));
        graph.addEdge(new Edge("B", "A", Edge.EdgeType.CALLS)); // 环路边

        // 假设 B 发生了变更，逆向追踪上游受波及的路径
        List<List<String>> paths = graph.findImpactedPaths("B");

        // 验证没有出现死循环，且成功找回了起始调用点 C
        assertFalse(paths.isEmpty(), "波及路径不应为空");
        // 因为有环路，且 BFS 全局去重，最终链路应该包含 C -> A -> B
        boolean foundC = false;
        for (List<String> p : paths) {
            if (p.contains("C") && p.contains("A") && p.contains("B")) {
                foundC = true;
                break;
            }
        }
        assertTrue(foundC, "应该能够成功穿透环路追溯到根源 C");
    }
}
