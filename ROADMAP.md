# OpenJTrace 3-Month Roadmap

本项目旨在以务实、可交付、渐进式的方式逐步完善 Java 生态的多仓依赖与链路影响分析能力。以下是未来 3 个月的详细开发路线图：

---

## 📅 Month 1: 核心链路增强与方法级高精度分析

### 目标
提升 Spring Controller、Dubbo 及 MyBatis 的解析精度，消除假阳性/假阴性，确保核心路径在 90% 以上的企业级场景中准确无误。

### 交付项
- **高精度方法重载与类型绑定** (`parser-java`)
  - 接入 JavaParser 的 `SymbolSolver` 机制，解析复杂类继承、接口实现下的方法调用。
  - 精确处理方法重载（同名不同参）情况，保证调用链路不发生“张冠李戴”。
- **Spring 配置与多口径端点解析** (`analyzer-http`)
  - 支持解析 `application.yml` 或 `application.properties` 中的 `server.port` 和 `server.servlet.context-path`。
  - 支持多路径注解匹配（例如 `@RequestMapping(value = {"/api/v1/user", "/api/user"})`）。
- **MyBatis 复杂场景支持** (`analyzer-mybatis`)
  - 支持解析 XML 中的 `<resultMap>` 关联查询、动态 SQL 标签（如 `<if>`, `<foreach>`）中引用的其他列或表。
  - 核心分析覆盖率达到 85% 以上，新增 30+ 组真实场景单元测试。

---

## 📅 Month 2: 主流消息队列 (MQ) 支持与 Git Diff 靶向分析

### 目标
打通异步消息链路，并引入 Git 智能对比，实现仅对变更代码进行“靶向扫描”。

### 交付项
- **主流消息队列 (MQ) 链路提取** (`analyzer-mq`)
  - **RocketMQ**: 解析 `RocketMQTemplate.send()` 等发送端调用，与 `@RocketMQMessageListener` 消费端建立关联。
  - **RabbitMQ / Spring AMQP**: 解析 `RabbitTemplate.convertAndSend()` 与 `@RabbitListener` 的绑定。
  - **Kafka**: 解析 `KafkaTemplate.send()` 与 `@KafkaListener` 的绑定。
- **基于 Git Diff 的靶向变更扫描** (`cli`)
  - 在 CLI 模块中，支持通过参数 `--git-diff [branch/commit]` 自动对比当前工作区与目标分支的代码差异。
  - 自动定位被修改的 Java 类、方法名以及 MyBatis XML 文件，作为 Trace 的起始节点（Target Nodes），免去手动输入类名和方法名的繁琐。

---

## 📅 Month 3: 构建工具集成、CI/CD 自动化与本地体验优化

### 目标
使 OpenJTrace 成为开发者日常工作流和企业 CI/CD Pipeline 的标配工具。

### 交付项
- **Maven/Gradle 插件开发**
  - 发布 `openjtrace-maven-plugin`，支持在 Maven 编译生命周期中直接运行影响分析。
  - 当检测到破坏性变更（例如被修改的方法有外部依赖且无向下兼容）时，支持配置构建中断。
- **CI/CD Markdown 报告输出**
  - CLI 工具支持直接输出可以直接贴入 GitHub PR 或 GitLab MR 的 Markdown 简版报告，高亮展示波及范围拓扑。
- **命令行 ASCII 交互依赖树**
  - 提供富文本命令行界面，支持在终端中交互式折叠/展开受波及的微服务调用链树形图。
