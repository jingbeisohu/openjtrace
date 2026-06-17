package org.openjtrace.analyzer.mybatis;

import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Edge;
import org.openjtrace.graph.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.List;

public class MyBatisAnalyzer {

    public void analyze(List<File> dirs, DependencyGraph graph) {
        for (File dir : dirs) {
            scanAndParseXml(dir, graph);
        }
    }

    private void scanAndParseXml(File file, DependencyGraph graph) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    scanAndParseXml(child, graph);
                }
            }
        } else if (file.getName().endsWith(".xml")) {
            try {
                parseMyBatisXml(file, graph);
            } catch (Exception e) {
                // Keep robust for malformed XMLs
            }
        }
    }

    private void parseMyBatisXml(File xmlFile, DependencyGraph graph) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // 禁用外部 DTD 验证以防扫描慢或离线时报错
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        if (!root.getTagName().equals("mapper")) {
            return; // 不是 MyBatis Mapper 文件
        }

        String namespace = root.getAttribute("namespace");
        if (namespace == null || namespace.trim().isEmpty()) {
            return;
        }

        String repoName = getRepoName(xmlFile.getAbsolutePath());

        // 解析 select, insert, update, delete 标签
        String[] sqlTags = {"select", "insert", "update", "delete"};
        for (String tag : sqlTags) {
            NodeList list = root.getElementsByTagName(tag);
            for (int i = 0; i < list.getLength(); i++) {
                Element sqlElement = (Element) list.item(i);
                String id = sqlElement.getAttribute("id");
                if (id == null || id.trim().isEmpty()) {
                    continue;
                }

                String mapperMethodId = namespace + "#" + id;
                String sqlNodeId = "SQL:" + mapperMethodId;

                // 创建 SQL 节点
                Node sqlNode = new Node(sqlNodeId, Node.NodeType.SQL_QUERY, tag + " " + id);
                sqlNode.setFilePath(xmlFile.getAbsolutePath());
                sqlNode.setRepoName(repoName);
                sqlNode.getMetadata().put("sqlStatement", sqlElement.getTextContent().trim());
                graph.addNode(sqlNode);

                // 确保 Mapper 方法的节点存在
                Node mapperNode = new Node(mapperMethodId, Node.NodeType.METHOD, id);
                mapperNode.setClassName(namespace.substring(namespace.lastIndexOf('.') + 1));
                mapperNode.setPackageName(namespace.contains(".") ? namespace.substring(0, namespace.lastIndexOf('.')) : "");
                mapperNode.setRepoName(repoName);
                graph.addNode(mapperNode);

                // 建立边：Mapper 接口方法 ─(MAPS_TO)─> XML SQL 语句
                graph.addEdge(new Edge(mapperMethodId, sqlNodeId, Edge.EdgeType.MAPS_TO));
            }
        }
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
