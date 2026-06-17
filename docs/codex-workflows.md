# Codex & AI Agent Integration Workflows

OpenJTrace 为 AI 编码助手（如 Codex、Antigravity）和 CI 自动化平台提供了精准的代码依赖元数据。以下是推荐的典型 AI Agent 协同工作流指南。

---

## 🤖 1. 智能 Code Review 准入流 (PR Review)

当开发者在 GitHub 或 GitLab 提交 Pull Request 时，AI CR Agent 可以借助 OpenJTrace 自动诊断变更范围，并在 PR 评论中提交直观的影响分析。

### 工作流步骤
1. **触发 PR 构建**：开发者向 `main` 分支提 PR。
2. **运行靶向扫描**：CI Pipeline（如 GitHub Actions）拉取代码，自动识别发生变更的方法（例如通过 `git diff` 识别被改动的类与方法），并运行 OpenJTrace CLI。
3. **调用 AI Agent 生成评估**：
   - CI Pipeline 将 OpenJTrace 生成的依赖链 JSON 数据传给 AI Agent。
   - AI Agent 根据调用链，分析该变更是否被其他仓库频繁使用，是否包含 Breaking Changes，以及是否需要特定的回归测试。
4. **自动生成 PR 评论**：AI Agent 自动在 PR 中留言：
   > ### 🔍 OpenJTrace 变更影响报告
   > 
   > 本次 PR 修改了 `common-db` 中的 `UserMapper.xml#updateUserStatus` 方法。
   > 
   > **⚠️ 跨仓波及风险评估**：
   > * **影响上游服务**：2 个 (`user-service`, `portal-api`)
   > * **受影响的 HTTP 接口**：
   >   - `POST /api/v1/user/status` (在 `portal-api` 仓中)
   > * **受影响的 RPC 服务**：
   >   - `UserService#updateStatus` (在 `user-service` 仓中)
   > 
   > **💡 建议测试范围**：
   > 请重点对 `portal-api` 的 `/api/v1/user/status` 接口进行集成测试。

---

## 🧪 2. 精准靶向回归测试生成 (Test Generation)

当大型项目发生重构时，回归测试往往耗时漫长。借助 OpenJTrace，测试 Agent 可以只针对波及的端点编写或执行用例。

### 工作流步骤
1. **精确定位变更点**：检测到 `payment-core` 仓库中的扣款方法被修改。
2. **追溯受影响的 API**：运行 OpenJTrace 发现仅 `POST /pay/submit` 和 `POST /pay/refund` 两个接口会被波及，其他 50 多个支付相关 API 均未受到影响。
3. **Agent 自动生成用例**：AI 自动测试 Agent (如 TestAgent) 根据上述 2 个接口的入参格式，针对性地编写并执行集成测试用例，并在测试报告中附带“基于影响分析的局部回归验证通过”说明。
4. **极速 CI 反馈**：原本需要 1 小时的全量集成测试，现在可在 2 分钟内靶向完成。

---

## 🔒 3. 三方依赖 CVE 安全漏洞受灾半径评估

当检测到某个三方包存在严重漏洞（如著名的 Log4j 漏洞）时，传统的漏洞扫描工具（如 Dependency-Check）只会报告“哪些服务引入了此 JAR”。但这可能产生大量误报（例如，很多服务只是引入了包，但由于业务完全没用到该敏感类，因此没有实质风险）。

### 工作流步骤
1. **安全预警触发**：检测到 `fastjson` 的 `com.alibaba.fastjson.JSON#parseObject` 存在远程代码执行漏洞。
2. **静态链路回溯**：
   - 使用 OpenJTrace 扫描全仓。
   - 以 `com.alibaba.fastjson.JSON#parseObject` 为 Target Node。
   - 逆向追踪所有可能到达该方法的调用链。
3. **判定受灾等级**：
   - 若某服务引入了该包，但在 OpenJTrace 的图模型中，没有任何业务方法到该方法的调用边 ➜ 判定为**安全风险低**，无需紧急封版修复。
   - 若发现有直接/间接调用链且暴露出外网 HTTP 接口 ➜ 判定为**高危**，自动触发 AI Agent 提交紧急修复 PR 并自动拉长部署阻断。
