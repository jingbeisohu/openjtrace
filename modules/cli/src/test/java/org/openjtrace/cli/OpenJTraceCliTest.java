package org.openjtrace.cli;

import org.junit.jupiter.api.Test;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class OpenJTraceCliTest {

    @Test
    public void testCliIntegrationScan() throws Exception {
        // examples 目录在项目根目录下
        // 运行测试时，当前工作目录通常是 cli 子模块的目录，因此 examples 的相对路径为 ../../examples
        File examplesDir = new File("../../examples");
        if (!examplesDir.exists()) {
            // 兜底：如果运行根目录的测试，可能相对路径就是 ./examples
            examplesDir = new File("examples");
        }
        
        assertTrue(examplesDir.exists(), "Examples 目录应当存在于工作区中");

        File tempReport = File.createTempFile("openjtrace-report-test", ".html");
        tempReport.deleteOnExit();

        // 实例化 CLI 并模拟调用参数
        OpenJTraceCli cli = new OpenJTraceCli();
        
        // 利用反射或直接设置参数（因 JCommander 参数为 private 字段，可以通过命令行参数的形式或者在测试里反射赋值）
        // 这里我们可以通过命令行参数形式初始化
        String[] args = {
            "-dirs", examplesDir.getAbsolutePath(),
            "-target", "SQL:org.openjtrace.example.mybatis.UserMapper#selectById",
            "-output", tempReport.getAbsolutePath()
        };

        // 运行 Cli
        OpenJTraceCli.main(args);

        // 验证报告文件是否生成且不为空
        assertTrue(tempReport.exists(), "HTML 报告文件应该被成功创建");
        assertTrue(tempReport.length() > 0, "生成的报告文件不能是空文件");
    }
}
