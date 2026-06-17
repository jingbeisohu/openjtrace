package org.openjtrace.analyzer.mq;

import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Edge;
import org.openjtrace.graph.Node;
import org.openjtrace.parser.java.JavaSourceParser.JavaClassMeta;
import org.openjtrace.parser.java.JavaSourceParser.JavaMethodMeta;
import org.openjtrace.parser.java.JavaSourceParser.MethodCall;

import java.util.List;

public class MqAnalyzer {
    
    public void analyze(List<JavaClassMeta> classes, DependencyGraph graph) {
        for (JavaClassMeta classMeta : classes) {
            analyzeConsumers(classMeta, graph);
            analyzeProducers(classMeta, graph);
        }
    }

    private void analyzeConsumers(JavaClassMeta classMeta, DependencyGraph graph) {
        String repoName = getRepoName(classMeta.getFile().getAbsolutePath());

        // 1. RocketMQ 消费者检测
        if (classMeta.getAnnotations().containsKey("RocketMQMessageListener")) {
            String annotationVal = classMeta.getAnnotations().get("RocketMQMessageListener");
            String topic = getAnnotationAttribute(annotationVal, "topic");
            if (topic.isEmpty()) {
                topic = "unknown-rocketmq-topic";
            }

            for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
                if (methodMeta.getName().equals("onMessage")) {
                    linkTopicToMethod(topic, classMeta, methodMeta, repoName, graph);
                }
            }
        }

        // 2. RabbitMQ 消费者检测 (类级或方法级)
        boolean hasClassRabbitListener = classMeta.getAnnotations().containsKey("RabbitListener");
        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            boolean hasMethodRabbitListener = methodMeta.getAnnotations().containsKey("RabbitListener") 
                    || methodMeta.getAnnotations().containsKey("RabbitHandler");
            
            if (hasClassRabbitListener || hasMethodRabbitListener) {
                String annotationVal = methodMeta.getAnnotations().containsKey("RabbitListener") ?
                        methodMeta.getAnnotations().get("RabbitListener") : classMeta.getAnnotations().get("RabbitListener");
                
                String queue = getAnnotationAttribute(annotationVal, "queues");
                if (queue.isEmpty()) {
                    queue = "unknown-rabbitmq-queue";
                }
                linkTopicToMethod(queue, classMeta, methodMeta, repoName, graph);
            }
        }
    }

    private void linkTopicToMethod(String topic, JavaClassMeta classMeta, JavaMethodMeta methodMeta, String repoName, DependencyGraph graph) {
        String mqNodeId = "MQ:" + topic;
        String methodNodeId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

        // 注册 MQ Topic 节点
        Node mqNode = new Node(mqNodeId, Node.NodeType.MQ_TOPIC, "MQ Topic: " + topic);
        mqNode.setRepoName(repoName);
        graph.addNode(mqNode);

        // 注册方法节点
        Node methodNode = new Node(methodNodeId, Node.NodeType.METHOD, methodMeta.getName());
        methodNode.setClassName(classMeta.getClassName());
        methodNode.setPackageName(classMeta.getPackageName());
        methodNode.setFilePath(classMeta.getFile().getAbsolutePath());
        methodNode.setRepoName(repoName);
        graph.addNode(methodNode);

        // 建立边：MQ 消息触发 ➜ 消费者方法被调用
        graph.addEdge(new Edge(mqNodeId, methodNodeId, Edge.EdgeType.CALLS));
    }

    private void analyzeProducers(JavaClassMeta classMeta, DependencyGraph graph) {
        String repoName = getRepoName(classMeta.getFile().getAbsolutePath());

        for (JavaMethodMeta methodMeta : classMeta.getMethods()) {
            String sourceMethodId = classMeta.getQualifiedName() + "#" + methodMeta.getName();

            for (MethodCall call : methodMeta.getMethodCalls()) {
                boolean isMqSend = call.getName().equals("convertAndSend") || call.getName().equals("send");
                boolean isTemplate = call.getScope().toLowerCase().contains("template");

                if (isMqSend && isTemplate && !call.getArguments().isEmpty()) {
                    // 获取发送的第一个参数 (即 Topic / Queue / Exchange)
                    String rawTopicArg = call.getArguments().get(0);
                    String topic = cleanLiteral(rawTopicArg);

                    if (!topic.isEmpty()) {
                        String mqNodeId = "MQ:" + topic;

                        // 注册 MQ Topic 节点
                        Node mqNode = new Node(mqNodeId, Node.NodeType.MQ_TOPIC, "MQ Topic: " + topic);
                        mqNode.setRepoName(repoName);
                        graph.addNode(mqNode);

                        // 注册发送者方法节点
                        Node senderNode = new Node(sourceMethodId, Node.NodeType.METHOD, methodMeta.getName());
                        senderNode.setClassName(classMeta.getClassName());
                        senderNode.setPackageName(classMeta.getPackageName());
                        senderNode.setFilePath(classMeta.getFile().getAbsolutePath());
                        senderNode.setRepoName(repoName);
                        graph.addNode(senderNode);

                        // 建立边：发送者方法 ➜ 向 MQ Topic 发送消息
                        graph.addEdge(new Edge(sourceMethodId, mqNodeId, Edge.EdgeType.CALLS));
                    }
                }
            }
        }
    }

    private String getAnnotationAttribute(String annotationStr, String attributeName) {
        if (annotationStr == null) return "";
        int attrIndex = annotationStr.indexOf(attributeName);
        if (attrIndex != -1) {
            int start = annotationStr.indexOf("\"", attrIndex);
            if (start != -1) {
                int end = annotationStr.indexOf("\"", start + 1);
                if (end != -1) {
                    return annotationStr.substring(start + 1, end);
                }
            }
        } else {
            // 如果只有单一值，如 @RabbitListener("my-queue")
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
        return ""; // 若不是字符串常量，静态分析中无法提取具体的值
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
