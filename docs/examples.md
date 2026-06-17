# OpenJTrace Examples

本文档展示几个典型的多仓链路变更影响分析示例，帮助理解 OpenJTrace 是如何串联不同仓库和技术的。

---

##  Scenario 1: 底层 MyBatis SQL 变更影响上游 HTTP 接口

在典型的电商系统中，我们有三个仓库/服务：
1. `order-service` (订单服务，暴露 HTTP API)
2. `member-service` (会员服务，暴露 Dubbo RPC 接口)
3. `common-db` (包含通用 MyBatis Mapper 与 XML)

### 依赖关系
* `member-service` 依赖 `common-db`
* `order-service` 通过 Dubbo 调用 `member-service` 的会员查询接口。

### 场景描述
开发人员修改了 `common-db` 中 `MemberMapper.xml` 的查询 SQL 逻辑：
```xml
<!-- common-db 仓库中的 MemberMapper.xml -->
<mapper namespace="com.example.db.MemberMapper">
    <select id="queryMemberStatus" resultType="com.example.db.Member">
        SELECT id, name, status FROM member WHERE id = #{id}
    </select>
</mapper>
```

### 运行 OpenJTrace 扫描
开发人员通过 CLI 运行：
```bash
java -jar openjtrace-cli.jar \
  -dirs ./order-service,./member-service,./common-db \
  -target "com.example.db.MemberMapper#queryMemberStatus" \
  -output ./report.html
```

### 追踪分析链路
OpenJTrace 的解析与图关联路径如下：
1. **MyBatis XML 映射**：定位到 `MemberMapper.xml` 中的 `queryMemberStatus` 对应 Java 接口方法 `com.example.db.MemberMapper#queryMemberStatus`。
2. **方法调用分析**：在 `member-service` 中，静态分析发现 `MemberServiceImpl.java` 调用了上述 Mapper 的该方法。
3. **Dubbo 服务发布**：识别出 `MemberServiceImpl` 类被标记了 `@DubboService(interfaceClass = MemberService.class)`。
4. **Dubbo 服务消费**：在 `order-service` 中，识别出 `OrderController.java` 中有一个被 `@DubboReference` 标记的 `MemberService` 类型的变量，且在它的 `getOrderDetail` 方法中调用了 `MemberService#queryMemberStatus`。
5. **HTTP 接口暴露**：识别出 `OrderController` 类上有 `@RestController`，其 `getOrderDetail` 方法上标记了 `@GetMapping("/order/detail")`。

### 最终输出的受影响报告
* **受影响的源头**: `com.example.db.MemberMapper#queryMemberStatus`
* **受影响的终点**: 
  - `[HTTP API] GET /order/detail` (位于 `order-service` 仓库)
  - `[Dubbo Service] com.example.service.MemberService#queryMemberStatus` (位于 `member-service` 仓库)
* **影响链条**:
  `MemberMapper.queryMemberStatus (XML SQL)` 
  ➜ `MemberMapper.queryMemberStatus() (Java Mapper)`
  ➜ `MemberServiceImpl.queryMemberStatus()`
  ➜ `MemberService.queryMemberStatus() (Dubbo Service)`
  ➜ `MemberService.queryMemberStatus() (Dubbo Reference)`
  ➜ `OrderController.getOrderDetail()`
  ➜ `GET /order/detail (HTTP API)`

---

## ⚙️ Scenario 2: Feign 客户端的 HTTP 变更影响

在微服务场景下，服务间调用有时不使用 Dubbo，而是使用 Spring Cloud Feign 声明式客户端。

### 仓库关系
* `inventory-service` (库存服务，提供 HTTP 接口 `/inventory/check`)
* `order-service` (订单服务，通过 Feign 调用库存服务)

### 变更源
在 `inventory-service` 中，修改了 `InventoryController.java` 的接口路径，或者更改了接收参数：
```java
// inventory-service 仓库
@RestController
public class InventoryController {
    @GetMapping("/inventory/check-stock") // 接口路径发生了变更
    public boolean checkStock(@RequestParam("sku") String sku, @RequestParam("qty") int qty) {
        ...
    }
}
```

### 运行扫描
```bash
java -jar openjtrace-cli.jar \
  -dirs ./inventory-service,./order-service \
  -target "com.example.inventory.InventoryController#checkStock"
```

### 追踪分析链路
1. 提取出 `InventoryController#checkStock` 的 HTTP API 节点：`GET /inventory/check-stock`。
2. 在 `order-service` 中解析 `@FeignClient(name = "inventory-service")` 标记的 `InventoryClient` 接口：
   ```java
   @FeignClient("inventory-service")
   public interface InventoryClient {
       @GetMapping("/inventory/check-stock") // 匹配端点
       boolean check(@RequestParam("sku") String sku, @RequestParam("qty") int qty);
   }
   ```
3. 通过 URL 路径和 Method (`GET`) 的静态串联，OpenJTrace 成功建立连接：
   `InventoryController.checkStock` (服务端 HTTP API)
   └─ `InventoryClient.check` (Feign 客户端方法)
4. 继续逆向寻找 `order-service` 内部哪些业务方法调用了 `InventoryClient.check`，输出上游受波及的 HTTP 入口（如 `OrderController#createOrder`）。
