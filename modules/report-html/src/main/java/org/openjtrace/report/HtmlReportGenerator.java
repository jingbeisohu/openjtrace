package org.openjtrace.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Edge;
import org.openjtrace.graph.Node;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HtmlReportGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class ReportJsonData {
        public List<Node> nodes = new ArrayList<>();
        public List<Edge> edges = new ArrayList<>();
        public List<List<String>> paths = new ArrayList<>();
        public String targetNode = "";
        public String generatedAt = "";
    }

    public void generate(DependencyGraph graph, List<List<String>> paths, String targetNodeId, File outputFile) throws Exception {
        ReportJsonData data = new ReportJsonData();
        data.nodes.addAll(graph.getNodes().values());
        data.edges.addAll(graph.getEdges());
        data.paths = paths;
        data.targetNode = targetNodeId;
        data.generatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        String jsonStr = objectMapper.writeValueAsString(data);

        // 读取模板
        String template = readTemplate();
        
        // 替换占位符
        String renderedHtml = template.replace("/*DATA_PLACEHOLDER*/", jsonStr);

        // 写入输出文件
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            writer.write(renderedHtml);
        }
    }

    private String readTemplate() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("template.html");
        if (is == null) {
            throw new FileNotFoundException("template.html not found in classpath");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
