package org.openjtrace.parser.maven;

import org.openjtrace.graph.DependencyGraph;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

/**
 * <h1>Maven 项目工程结构解析器 (MavenProjectParser)</h1>
 * <p>
 * 该类负责静态遍历和扫描指定的本地目录，定位所有的 {@code pom.xml} 文件。
 * 利用 W3C XML DOM 解析器提取每个模块的 GAV (GroupId, ArtifactId, Version) 坐标，
 * 继承 Parent 模块坐标，并搜集子模块下的所有的依赖库信息。
 * </p>
 * 
 * <p>
 * 该模块的作用是建立多仓/多模块的物理坐标清单，支持后续静态分析将独立的微服务工程节点
 * 映射到统一的 Maven 依赖管理边界空间内。
 * </p>
 */
public class MavenProjectParser {

    public static class MavenModule {
        private String groupId;
        private String artifactId;
        private String version;
        private File pomFile;
        private List<String> dependencies = new ArrayList<>(); // formats: "groupId:artifactId"

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public File getPomFile() {
            return pomFile;
        }

        public List<String> getDependencies() {
            return dependencies;
        }
    }

    private List<MavenModule> modules = new ArrayList<>();

    public List<MavenModule> parse(List<File> repoDirs) {
        for (File dir : repoDirs) {
            findAndParsePoms(dir);
        }
        return modules;
    }

    private void findAndParsePoms(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    findAndParsePoms(child);
                }
            }
        } else if (file.getName().equals("pom.xml")) {
            try {
                MavenModule module = parsePom(file);
                if (module != null) {
                    modules.add(module);
                }
            } catch (Exception e) {
                // Ignore parsing errors for robust scanning
            }
        }
    }

    private MavenModule parsePom(File pomFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        
        String groupId = getTagValue(root, "groupId");
        String artifactId = getTagValue(root, "artifactId");
        String version = getTagValue(root, "version");

        // 如果子 POM 没有声明 groupId/version，可以尝试向上继承 parent 的
        if (groupId.isEmpty() || version.isEmpty()) {
            NodeList parentList = root.getElementsByTagName("parent");
            if (parentList.getLength() > 0) {
                Element parent = (Element) parentList.item(0);
                if (groupId.isEmpty()) {
                    groupId = getTagValue(parent, "groupId");
                }
                if (version.isEmpty()) {
                    version = getTagValue(parent, "version");
                }
            }
        }

        if (artifactId.isEmpty()) {
            return null;
        }

        MavenModule module = new MavenModule();
        module.groupId = groupId;
        module.artifactId = artifactId;
        module.version = version;
        module.pomFile = pomFile;

        // 解析依赖关系
        NodeList depList = root.getElementsByTagName("dependency");
        for (int i = 0; i < depList.getLength(); i++) {
            Element dep = (Element) depList.item(i);
            String depGroupId = getTagValue(dep, "groupId");
            String depArtifactId = getTagValue(dep, "artifactId");
            if (!depGroupId.isEmpty() && !depArtifactId.isEmpty()) {
                module.dependencies.add(depGroupId + ":" + depArtifactId);
            }
        }

        return module;
    }

    private String getTagValue(Element element, String tagName) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            // 确保只获取直接子节点的值，而不是子依赖中的同名节点
            for (int i = 0; i < list.getLength(); i++) {
                org.w3c.dom.Node node = list.item(i);
                if (node.getParentNode().equals(element)) {
                    return node.getTextContent().trim();
                }
            }
            // 降级使用第一个
            return list.item(0).getTextContent().trim();
        }
        return "";
    }
}
