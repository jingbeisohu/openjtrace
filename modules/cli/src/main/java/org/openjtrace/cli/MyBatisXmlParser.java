package org.openjtrace.cli;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * <h1>MyBatis XML 依赖提取与定位工具 (MyBatisXmlParser)</h1>
 * <p>
 * 使用 JDK 自带的 StAX 解析器（不解析外部 DTD），静态抽取 Mapper XML 中的 namespace 属性
 * 以及各个 SQL ID（select/insert/update/delete）的物理起止行号，用以和 Git Diff 的改动行做相交校验。
 * </p>
 */
public class MyBatisXmlParser {

    public static class SqlLocation {
        private final String id;
        private final int startLine;
        private int endLine;

        public SqlLocation(String id, int startLine) {
            this.id = id;
            this.startLine = startLine;
        }

        public String getId() {
            return id;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public void setEndLine(int endLine) {
            this.endLine = endLine;
        }
    }

    public static class MyBatisXmlMeta {
        private String namespace;
        private final List<SqlLocation> sqlLocations = new ArrayList<>();

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public List<SqlLocation> getSqlLocations() {
            return sqlLocations;
        }
    }

    public static MyBatisXmlMeta parseMeta(File xmlFile) {
        MyBatisXmlMeta meta = new MyBatisXmlMeta();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        try {
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        } catch (Exception e) {
            // Ignore settings if unsupported
        }

        try (FileInputStream fis = new FileInputStream(xmlFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(fis);
            Stack<SqlLocation> stack = new Stack<>();
            int depth = 0;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    String name = reader.getLocalName();
                    if (depth == 1 && "mapper".equals(name)) {
                        meta.setNamespace(reader.getAttributeValue(null, "namespace"));
                    } else if (depth == 2 && ("select".equals(name) || "insert".equals(name) || "update".equals(name) || "delete".equals(name))) {
                        String id = reader.getAttributeValue(null, "id");
                        if (id != null && !id.trim().isEmpty()) {
                            int line = reader.getLocation().getLineNumber();
                            stack.push(new SqlLocation(id, line));
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if (depth == 2 && ("select".equals(name) || "insert".equals(name) || "update".equals(name) || "delete".equals(name))) {
                        if (!stack.isEmpty()) {
                            SqlLocation loc = stack.pop();
                            loc.setEndLine(reader.getLocation().getLineNumber());
                            meta.getSqlLocations().add(loc);
                        }
                    }
                    depth--;
                }
            }
        } catch (Exception e) {
            // Graceful degrade on malformed XML files
        }
        return meta;
    }
}
