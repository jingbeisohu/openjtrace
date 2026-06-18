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

/**
 * <h1>MyBatis Mapper 数据库依赖分析器 (MyBatisAnalyzer)</h1>
 * <p>
 * 该类用于扫描和静态解析指定目录下的所有 MyBatis XML 映射文件。
 * </p>
 * 
 * <h3>具体解析流：</h3>
 * <ul>
 *   <li>
 *     读取 XML 头并验证根节点为 {@code <mapper>}。
 *   </li>
 *   <li>
 *     提取 {@code namespace} 属性（绑定 Java Mapper 接口全限定名）。
 *   </li>
 *   <li>
 *     遍历所有的 SQL 语句子元素（{@code <select>}, {@code <insert>}, {@code <update>}, {@code <delete>}），
 *     提取它们的 {@code id}，在依赖图中创建 {@link Node.NodeType#SQL_QUERY} 类型的叶子节点。
 *   </li>
 *   <li>
 *     通过 MAPS_TO 关联边，建立从 Java Mapper 接口的方法节点直接指向 XML 具体 SQL 语句的边，
 *     打通代码修改与底层数据库 SQL 的影响链条。
 *   </li>
 * </ul>
 */
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
