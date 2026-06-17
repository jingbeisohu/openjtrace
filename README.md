# OpenJTrace

> **面向 Java 企业级开源生态的多仓依赖与变更影响分析工具**  
> `OpenJTrace` 旨在打破微服务、分布式及多仓库场景下的“代码迷雾”，为复杂的 Java 开源生态提供轻量级、精准的方法级/端点级变更影响分析。

---

## 💡 为什么我们需要 OpenJTrace？

### 1. 解决什么真实维护问题？
在现代 Java 企业级架构中，微服务化、多仓库开发已成为主流。但这带来了巨大的**链路审查难题**：
* **跨仓/跨服务迷雾**：开发人员修改了一个底层 Common 包或某个服务的 Dubbo/HTTP 接口，根本无法直观地知道哪些上游消费端仓库会受到波及。
* **多协议黑盒**：一个业务请求往往穿透了 `HTTP (Spring Controller) -> RPC (Dubbo) -> MQ (RocketMQ/Kafka) -> SQL (MyBatis XML)`。当表结构或底层 SQL 发生变动时，测试和 Code Review 很难精准识别受波及的 HTTP 入口。
* **过度测试与漏测**：由于缺乏精准的影响范围分析，团队要么为了安全起见进行“全量回归测试”（极大浪费资源），要么凭经验进行局部测试（导致线上漏测）。

### 2. 为什么对 Java 开源生态极其重要？
* **庞大的存量生态**：Java 拥有全球最大规模的企业级系统。Spring Cloud、Apache Dubbo 和 MyBatis 构成了国内乃至全球企业级 Java 的“黄金三角”生态。
* **工具链的巨大空白**：现有的 Java 影响分析工具（如一些商业静态分析、或基于 JVM Agent 的运行时探针）要么过于笨重、需要复杂的探针部署，要么仅支持单体/单仓库分析，**完全缺乏对多仓库、跨服务、多种企业级中间件（RPC/HTTP/MQ/SQL）进行统一静态解析和边连接的开源方案**。
* **协同成本高昂**：开源组件升级频繁，对于拥有成百上千个微服务节点的企业级开源生态来说，分析依赖项升级的影响往往依赖肉眼检索或全局 Grep，急需一款自动化的多仓影响分析工具。

---

## 🚀 当前可运行能力

`OpenJTrace` 绝非空中楼阁，它率先支持了 Java 企业级最核心的四大关联链路，具备**开箱即用**的静态扫描与分析能力：

1. **Maven 多仓拓扑解析 (`parser-maven`)**：扫描指定的多个本地仓库，读取 `pom.xml` 的 `GAV` (GroupId, ArtifactId, Version) 以及依赖关系，将分离的仓库通过依赖树建立物理关联。
2. **Spring Controller 路由提取 (`analyzer-http`)**：自动解析 `@RestController` 和 `@RequestMapping`（包含 `@GetMapping`, `@PostMapping` 等），提取完整的 HTTP 端点及其映射的 Java 方法。
3. **Dubbo 注解跨仓关联 (`analyzer-dubbo`)**：解析服务提供者端的 `@DubboService` / `@Service` 和服务消费者端的 `@DubboReference` / `@Reference`。利用 Java 接口全限定名将“调用端的方法引用”与“服务端的具体实现方法”进行跨仓拼接。
4. **MyBatis XML 映射绑定 (`analyzer-mybatis`)**：静态解析 Mapper 接口方法与 MyBatis XML 里的 SQL 语句 ID，自动绑定，将 SQL 级的修改关联至 Java 层的 Mapper 方法。

通过这套组合拳，`OpenJTrace` 能够完成如下链路的逆向追溯：
```text
[修改 MyBatis XML SQL] 
      │ (MyBatisAnalyzer)
      ▼
[Java Mapper 接口方法] 
      │ (JavaSourceParser)
      ▼
[Java 内部服务/业务逻辑方法] 
      │ (DubboAnalyzer)
      ▼
[Dubbo Reference (客户端)] ──── (跨仓 RPC 关联) ──── [Dubbo Service (服务端实现)]
                                                          │
                                                          ▼
                                                   [Spring Controller HTTP API]
```

---

## 🤖 Codex 如何降低维护负担？

作为面向未来智能 Agent（如 Codex、Antigravity）设计的工具，`OpenJTrace` 为 AI 协同提供了结构化的元数据支持：

* **智能 PR Review 准入**：在 CI/CD 中嵌入 `OpenJTrace`。当提交 PR 时，自动生成变更影响路径图，作为 PR 描述附件，告知评审人：“此 PR 修改了 `OrderMapper.xml` 的 SQL，将波及上游 3 个服务的 5 个 HTTP API”，极大减少人工审查负担。
* **精准测试用例生成 (Impact-based Testing)**：结合 AI Agent，只针对受影响的端点自动编写/执行集成测试，避免盲目测试全量接口，缩短测试反馈周期。
* **Issue 自动分发与分类 (Triage)**：当出现接口报错或数据库死锁时，Agent 可以利用 `OpenJTrace` 依赖图，反向追溯可能引入该问题的代码源头，自动指派给相关仓库的负责人。
* **自动化 Release Notes**：每次发布新版本时，自动通过影响图提取本次变更所影响的外部 API 变化列表，生成格式化的用户指南，防范破坏性变更（Breaking Changes）。
* **依赖安全漏洞半径分析**：当检测到某方库存在 CVE 漏洞时，不仅指出哪些项目引入了它，更能通过静态方法调用链，分析业务代码是否真正“调用”到了受漏洞影响的敏感方法，评估真实受灾半径。

---

## 🗺️ 未来 3 个月 Roadmap

我们追求务实、高可交付的迭代节奏，以下是未来 3 个月的里程碑计划：

### Month 1: 核心链路增强与方法级高精度关联 (7月)
* **精确的类型推导与方法重载解析**：增强 `parser-java` 的类型绑定，支持多态、重载方法解析，降低调用链误报率。
* **Spring Boot 配置文件解析**：读取 `application.yml/properties` 中的 `server.port` 和 `context-path`，使提取出的 HTTP 接口具备绝对的 URL 地址。
* **集成测试覆盖率达到 80%**。

### Month 2: 主流消息队列 (MQ) 与代码 Diff 自动识别 (8月)
* **MyBatis-Plus 支持**：兼容 MyBatis-Plus 动态生成的通用 CRUD 方法（无需 XML）。
* **主流 MQ (RocketMQ/Kafka) 链路追踪**：支持提取并关联 RocketMQ / Kafka / RabbitMQ 的 Producer（发送端）与 `@RocketMQMessageListener` / `@RabbitListener`（消费端），打通异步调用链。
* **基于 Git Diff 的靶向扫描**：实现 `openjtrace-cli` 自动对比当前分支与 master 分支的 git diff，自动识别发生变更的源文件列表，无需手动输入 target。

### Month 3: 工具链生态集成与 IDE 插件原型 (9月)
* **Maven/Gradle 插件**：发布 `openjtrace-maven-plugin`，使影响分析能够轻松集成到主流 CI/CD 流程中。
* **本地化 CLI 体验**：提供交互式命令行终端，支持在终端以 ASCII 树状图实时展开受影响的链路。
* **IDE (IntelliJ IDEA) 影响分析插件原型**：开发 IDEA 插件，在 Mapper 方法或 RPC 接口旁边提供“影响分析”侧边栏，右键即可在 IDE 内直观预览上游波及范围。

---

## 🛠️ 开始使用

### 编译构建
```bash
git clone https://github.com/openjtrace/openjtrace.git
cd openjtrace
mvn clean package
```

### 运行 CLI 扫描
```bash
java -jar modules/cli/target/openjtrace-cli.jar \
  -dirs /path/to/repo-a,/path/to/repo-b \
  -target "org.openjtrace.example.mybatis.UserMapper#selectById" \
  -output ./impact-report.html
```

---

## 🤝 参与贡献

欢迎通过提交 Issue 或 Pull Request 来加入我们！详情请参阅 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 📄 开源协议

本项目采用 [Apache-2.0 License](LICENSE) 开源协议。
