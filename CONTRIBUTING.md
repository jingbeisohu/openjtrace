# Contributing to OpenJTrace

感谢您有兴趣为 OpenJTrace 做出贡献！我们非常欢迎来自社区的 Issue 报告、文档改进和代码提交。

## 💡 开发环境准备

* **JDK 版本**: OpenJTrace 基于 **Java 17** 开发，建议使用 JDK 17 及以上版本进行编译和调试。
* **构建工具**: 使用 **Maven 3.8+**。
* **IDE 推荐**: IntelliJ IDEA，导入后请启用 Lombok 支持。

### 编译与运行测试
在根目录下运行：
```bash
mvn clean test
```

## 🛠️ 分支规范

* `main` 分支是稳定的发布分支，请勿直接向 `main` 分支推送代码。
* 所有的功能开发和 Bug 修复均应在独立的特性分支（如 `feature/xxx` 或 `bugfix/xxx`）上进行，并向 `main` 提交 Pull Request。

## 📝 提交信息规范 (Commit Message)

我们遵循 Conventional Commits 规范。格式如下：
```text
<type>(<scope>): <subject>
```

常见的 `type` 包含：
* `feat`: 引入新功能。
* `fix`: 修复 Bug。
* `docs`: 仅修改文档。
* `style`: 不影响代码逻辑的格式修改（空格、分号等）。
* `refactor`: 重构代码（既非新增功能也非修复 Bug）。
* `test`: 增加或修改测试用例。
* `chore`: 构建过程或辅助工具的变动。

示例：
```text
feat(parser-java): add support for @DubboReference parsing
fix(analyzer-mybatis): resolve NPE when namespace is empty in XML
```

## 🔍 PR 准入要求

1. 所有的 PR 都必须通过现有的单元测试与集成测试。
2. 如果引入了新的功能或修改了已有逻辑，请务必编写对应的测试用例（位于各模块的 `src/test/java` 下）。
3. 代码改动需遵循已有的代码风格。
