package org.openjtrace.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.openjtrace.analyzer.dubbo.DubboAnalyzer;
import org.openjtrace.analyzer.http.HttpAnalyzer;
import org.openjtrace.analyzer.mybatis.MyBatisAnalyzer;
import org.openjtrace.graph.DependencyGraph;
import org.openjtrace.graph.Node;
import org.openjtrace.parser.java.JavaSourceParser;
import org.openjtrace.parser.java.JavaSourceParser.JavaClassMeta;
import org.openjtrace.parser.maven.MavenProjectParser;
import org.openjtrace.report.HtmlReportGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <h1>OpenJTrace 命令行工具主入口 (OpenJTraceCli)</h1>
 * <p>
 * 该类是整个工具的核心启动入口，基于 {@code JCommander} 解析命令行参数。
 * 负责调度 Maven、Java 源码解析器以及 HTTP、Dubbo、MyBatis、MQ 各协议分析器模块。
 * </p>
 * 
 * <h3>可配置命令行参数：</h3>
 * <ul>
 *   <li>{@code -dirs} (必填) - 待分析的项目或多个本地 Git 仓库路径列表，逗号分隔</li>
 *   <li>{@code -target} (必填) - 代码或 SQL 变更的起点节点 ID</li>
 *   <li>{@code -output} (可选) - 本地 HTML 影响分析报告输出路径</li>
 * </ul>
 * 
 * <h3>核心运行管道 (Pipeline)：</h3>
 * <ol>
 *   <li>扫描目标目录，解析所有的 {@code pom.xml} 并注册 Maven 拓扑。</li>
 *   <li>静态扫描目录下的所有 Java 源文件，生成抽象语法树 (AST)。</li>
 *   <li>启动并运行 HTTP、Dubbo、MyBatis、MQ 分析器，对提取出的方法、接口与 SQL 建立关联边。</li>
 *   <li>从传入的 {@code -target} 起点开始，沿图的反向邻接表运行 BFS 拓扑算法，计算出受波及的完整链条。</li>
 *   <li>在终端中格式化输出调用路径树，若指定了 {@code -output}，则生成交互式 HTML 可视化分析报告。</li>
 * </ol>
 */
public class OpenJTraceCli {

    @Parameter(names = "-dirs", description = "需要扫描的仓库或项目目录，多个以逗号分隔", required = true)
    private String dirs;

    @Parameter(names = "-target", description = "变更影响分析的起点节点 ID (如: org.example.UserMapper#selectById 或 SQL:org.example.UserMapper#selectById)", required = false)
    private String target;

    @Parameter(names = "--git-diff", description = "基于 Git Diff 的靶向代码变更影响分析，参数可以是分支名或 commit (如: master 或 HEAD~1)。对比本地未提交修改请使用 HEAD", required = false)
    private String gitDiff;

    @Parameter(names = "-output", description = "HTML 影响报告输出路径", required = false)
    private String output;

    public static void main(String[] args) {
        OpenJTraceCli cli = new OpenJTraceCli();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(cli)
                .build();
        try {
            jCommander.parse(args);
            cli.run();
        } catch (Exception e) {
            System.err.println("解析命令行参数或运行出错: " + e.getMessage());
            jCommander.usage();
            System.exit(1);
        }
    }

    public void run() throws Exception {
        System.out.println("====== [OpenJTrace] 开始扫描与分析 ======");

        if ((target == null || target.trim().isEmpty()) && (gitDiff == null || gitDiff.trim().isEmpty())) {
            throw new IllegalArgumentException("必须提供分析起点 -target 或指定 --git-diff 参数！");
        }
        
        List<File> scanDirs = new ArrayList<>();
        for (String dir : dirs.split(",")) {
            File f = new File(dir.trim());
            if (f.exists() && f.isDirectory()) {
                scanDirs.add(f);
                System.out.println(" 注册扫描目录: " + f.getAbsolutePath());
            } else {
                System.err.println("⚠️ 目录不存在或不是文件夹: " + dir);
            }
        }

        if (scanDirs.isEmpty()) {
            throw new IllegalArgumentException("没有有效的扫描目录！");
        }

        // 1. 解析 Maven 拓扑
        System.out.println("\n[1/5] 解析 Maven 依赖...");
        MavenProjectParser mavenParser = new MavenProjectParser();
        List<MavenProjectParser.MavenModule> modules = mavenParser.parse(scanDirs);
        System.out.println("   发现 Maven 模块数: " + modules.size());

        // 2. 解析 Java 源代码
        System.out.println("\n[2/5] 解析 Java 源代码...");
        JavaSourceParser javaParser = new JavaSourceParser();
        List<JavaClassMeta> classMetas = new ArrayList<>();
        for (File dir : scanDirs) {
            classMetas.addAll(javaParser.parseDirectory(dir));
        }
        System.out.println("   解析 Java 文件数: " + classMetas.size());

        // 3. 构建依赖图并运行分析器
        System.out.println("\n[3/5] 构建依赖关联图...");
        DependencyGraph graph = new DependencyGraph();

        // 3.1 HTTP 关联分析
        HttpAnalyzer httpAnalyzer = new HttpAnalyzer();
        httpAnalyzer.analyze(classMetas, graph);

        // 3.2 Dubbo RPC 关联分析
        DubboAnalyzer dubboAnalyzer = new DubboAnalyzer();
        dubboAnalyzer.analyze(classMetas, graph);

        // 3.3 MyBatis Mapper 关联分析
        MyBatisAnalyzer myBatisAnalyzer = new MyBatisAnalyzer();
        myBatisAnalyzer.analyze(scanDirs, graph);

        // 3.4 MQ 异步链路分析
        org.openjtrace.analyzer.mq.MqAnalyzer mqAnalyzer = new org.openjtrace.analyzer.mq.MqAnalyzer();
        mqAnalyzer.analyze(classMetas, graph);

        // 3.5 Redis 缓存关联分析
        org.openjtrace.analyzer.redis.RedisAnalyzer redisAnalyzer = new org.openjtrace.analyzer.redis.RedisAnalyzer();
        redisAnalyzer.analyze(classMetas, graph);

        // 3.6 MongoDB 依赖关联分析
        org.openjtrace.analyzer.mongodb.MongoAnalyzer mongoAnalyzer = new org.openjtrace.analyzer.mongodb.MongoAnalyzer();
        mongoAnalyzer.analyze(classMetas, graph);

        System.out.println("   图构建完毕: 节点数 = " + graph.getNodes().size() + ", 边数 = " + graph.getEdges().size());

        // 3.7 自动推导变更 Targets
        List<String> targetList = new ArrayList<>();
        if (gitDiff != null && !gitDiff.trim().isEmpty()) {
            System.out.println("\n[3.7] 基于 Git Diff 自动推导变更 Targets (Ref = " + gitDiff + ")...");
            List<GitDiffResolver.FileDiff> diffs = GitDiffResolver.resolveDiff(scanDirs, gitDiff);
            System.out.println("   解析到 Git Diff 变动文件数: " + diffs.size());
            
            for (GitDiffResolver.FileDiff fd : diffs) {
                File f = new File(fd.getFilePath());
                if (!f.exists()) continue;

                if (f.getName().endsWith(".java")) {
                    try {
                        JavaSourceParser.JavaClassMeta classMeta = javaParser.parseJavaFile(f);
                        if (classMeta != null) {
                            boolean methodMatched = false;
                            for (JavaSourceParser.JavaMethodMeta method : classMeta.getMethods()) {
                                for (int line : fd.getModifiedLines()) {
                                    if (line >= method.getStartLine() && line <= method.getEndLine()) {
                                        String mId = classMeta.getQualifiedName() + "#" + method.getName();
                                        if (!targetList.contains(mId)) {
                                            targetList.add(mId);
                                        }
                                        methodMatched = true;
                                    }
                                }
                            }
                            // 如果行号落入类声明内但未落入任何方法内（如改了字段），则将该类所有方法作为 target
                            if (!methodMatched) {
                                for (int line : fd.getModifiedLines()) {
                                    if (line >= classMeta.getStartLine() && line <= classMeta.getEndLine()) {
                                        for (JavaSourceParser.JavaMethodMeta method : classMeta.getMethods()) {
                                            String mId = classMeta.getQualifiedName() + "#" + method.getName();
                                            if (!targetList.contains(mId)) {
                                                targetList.add(mId);
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Keep scanning
                    }
                } else if (f.getName().endsWith(".xml")) {
                    MyBatisXmlParser.MyBatisXmlMeta xmlMeta = MyBatisXmlParser.parseMeta(f);
                    if (xmlMeta.getNamespace() != null) {
                        for (MyBatisXmlParser.SqlLocation loc : xmlMeta.getSqlLocations()) {
                            for (int line : fd.getModifiedLines()) {
                                if (line >= loc.getStartLine() && line <= loc.getEndLine()) {
                                    String sId = "SQL:" + xmlMeta.getNamespace() + "#" + loc.getId();
                                    if (!targetList.contains(sId)) {
                                        targetList.add(sId);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("   自动推导出 " + targetList.size() + " 个变动源 (Targets): " + targetList);
        } else {
            targetList.add(target.trim());
        }

        if (targetList.isEmpty()) {
            System.out.println("⚠️ 未在 Git Diff 中发现任何发生变动的 Java 方法或 MyBatis SQL ID，分析结束！");
            return;
        }

        // 4. 逆向寻找变更影响路径
        System.out.println("\n[4/5] 逆向影响链条追溯...");
        List<List<String>> allPaths = new ArrayList<>();
        for (String t : targetList) {
            System.out.println(" -> 追溯 Target = " + t);
            List<List<String>> paths = graph.findImpactedPaths(t);
            allPaths.addAll(paths);
        }
        
        if (allPaths.isEmpty()) {
            System.out.println(" 提示: 未发现受影响的上游调用链路或目标节点在图中不存在！");
        } else {
            System.out.println(" 发现受影响的调用链路共 " + allPaths.size() + " 条：");
            for (int i = 0; i < allPaths.size(); i++) {
                System.out.println("\n 🔗 链路 #" + (i + 1) + " (自上游接口 -> 变更源):");
                List<String> path = allPaths.get(i);
                for (int j = 0; j < path.size(); j++) {
                    String nodeId = path.get(j);
                    Node node = graph.getNode(nodeId);
                    String indent = "   ".repeat(j);
                    String typeStr = node != null ? "[" + node.getType() + "] " : "";
                    String label = node != null ? node.getName() : nodeId;
                    System.out.println(indent + "└── " + typeStr + label);
                }
            }
        }

        // 5. 生成报告
        if (output != null) {
            System.out.println("\n[5/5] 生成可视化 HTML 报告...");
            File outFile = new File(output);
            HtmlReportGenerator reportGenerator = new HtmlReportGenerator();
            String combinedTarget = String.join(", ", targetList);
            reportGenerator.generate(graph, allPaths, combinedTarget, outFile);
            System.out.println(" 报告已成功输出至: " + outFile.getAbsolutePath());
        }

        System.out.println("\n====== [OpenJTrace] 分析完成 ======");
    }
}
