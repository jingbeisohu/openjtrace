package org.openjtrace.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <h1>Git 变更解析工具 (GitDiffResolver)</h1>
 * <p>
 * 调度本地 {@code git diff -U0 [commit/branch]} 进程，并解析其统一差分输出。
 * 提取出发生变动的文件绝对路径，以及具体改动的行号列表，为后续的靶向分析提供变更源。
 * </p>
 */
public class GitDiffResolver {

    public static class FileDiff {
        private final String filePath;
        private final List<Integer> modifiedLines = new ArrayList<>();

        public FileDiff(String filePath) {
            this.filePath = filePath;
        }

        public String getFilePath() {
            return filePath;
        }

        public List<Integer> getModifiedLines() {
            return modifiedLines;
        }
    }

    /**
     * 在给定的目录列表中，调用 git 命令解析差异
     *
     * @param repoDirs     待扫描的仓库目录列表
     * @param gitDiffParam 命令行传入的分支名或 commit 标识符 (如: master / HEAD~1)，若为空则对比工作区与 HEAD 的差异
     * @return 每一个有改动的文件及其变动行号集合的列表
     */
    public static List<FileDiff> resolveDiff(List<File> repoDirs, String gitDiffParam) {
        List<FileDiff> fileDiffs = new ArrayList<>();
        for (File repoDir : repoDirs) {
            try {
                // 构造 git diff -U0 [commit/branch] 命令，-U0 可去除上下文只输出发生改动的行
                List<String> command = new ArrayList<>();
                command.add("git");
                command.add("diff");
                command.add("-U0");
                if (gitDiffParam != null && !gitDiffParam.trim().isEmpty() && !gitDiffParam.trim().equalsIgnoreCase("true")) {
                    command.add(gitDiffParam.trim());
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(repoDir);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    FileDiff currentFileDiff = null;
                    // 正则提取修改后最新文件路径：diff --git a/filename b/filename
                    Pattern filePattern = Pattern.compile("^diff --git a/.* b/(.*)$");
                    // 正则提取修改后文件的新增/修改行号起止区间：@@ -oldStart,oldLen +newStart,newLen @@
                    Pattern chunkPattern = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");

                    while ((line = reader.readLine()) != null) {
                        Matcher fileMatcher = filePattern.matcher(line);
                        if (fileMatcher.matches()) {
                            String relPath = fileMatcher.group(1);
                            File absoluteFile = new File(repoDir, relPath);
                            currentFileDiff = new FileDiff(absoluteFile.getAbsolutePath());
                            fileDiffs.add(currentFileDiff);
                            continue;
                        }

                        if (currentFileDiff != null) {
                            Matcher chunkMatcher = chunkPattern.matcher(line);
                            if (chunkMatcher.find()) {
                                int newStart = Integer.parseInt(chunkMatcher.group(1));
                                String lenGroup = chunkMatcher.group(2);
                                int newLength = (lenGroup == null) ? 1 : Integer.parseInt(lenGroup);

                                for (int i = 0; i < newLength; i++) {
                                    currentFileDiff.getModifiedLines().add(newStart + i);
                                }
                            }
                        }
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                System.err.println("❌ 在目录 " + repoDir.getAbsolutePath() + " 运行 git diff 失败: " + e.getMessage());
            }
        }
        return fileDiffs;
    }
}
