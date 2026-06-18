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

/**
 * <h1>交互式 HTML 影响报告生成器 (HtmlReportGenerator)</h1>
 * <p>
 * 该类负责将静态分析得到的依赖图数据以及最终计算出来的变更受波及路径列表，
 * 渲染为可交互、具有现代高奢暗色系 UI 风格的 HTML/CSS 报告。
 * </p>
 * 
 * <h3>工作流原理：</h3>
 * <ol>
 *   <li>
 *     使用 Jackson {@link ObjectMapper} 将节点、边结构以及分析路径路径序列化成 JSON 字符串。
 *   </li>
 *   <li>
 *     从 {@code ClassLoader} 资源中读取内置的 {@code template.html} 网页模版。
 *   </li>
 *   <li>
 *     替换模版中预留的 JSON 数据占位符 {@code /*DATA_PLACEHOLDER* /}，输出到指定的本地报告文件中。
 *   </li>
 * </ol>
 */
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
