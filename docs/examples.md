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

---

## ⚡ Scenario 3: Redis 缓存依赖与方法关联分析

在高并发系统中，我们经常使用 Redis 做缓存。当我们需要调整缓存的数据结构或 Key 的前缀设计时，也需要了解这会影响到哪些业务接口。

### 示例代码
在 `mybatis-demo` 中有以下关于缓存的服务类：
```java
// UserCacheService.java
@Service
public class UserCacheService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Cacheable(value = "user-cache")
    public String getUserCached(int id) {
        return "user:" + id;
    }

    public void updateSession(String token, String data) {
        // 静态分析器将提取 "user:session" 这个前缀字面量
        redisTemplate.opsForValue().set("user:session:" + token, data);
    }
}
```

### 运行 OpenJTrace 扫描
如果我们想分析 `REDIS:user-cache` 缓存节点或 `REDIS:user:session` 被修改后对上游的影响：
```bash
java -jar openjtrace-cli.jar \
  -dirs ./mybatis-demo \
  -target "REDIS:user-cache" \
  -output ./redis-report.html
```

### 追踪分析链路
OpenJTrace 的解析与图关联路径如下：
1. **Spring Cache 注解解析**：`analyzer-redis` 解析到 `UserCacheService#getUserCached` 方法上标注了 `@Cacheable(value = "user-cache")`，生成 `REDIS:user-cache` 节点，并创建 `UserCacheService#getUserCached ──(CALLS)──> REDIS:user-cache` 的依赖边。
2. **redisTemplate 调用提取**：静态分析发现 `updateSession` 方法内部调用了 `redisTemplate.opsForValue().set(...)`，且 Key 的前缀常量为 `"user:session:"`。分析器截取其常量前缀，生成 `REDIS:user:session` 节点，并创建 `UserCacheService#updateSession ──(CALLS)──> REDIS:user:session` 的依赖边。

### 最终输出的受影响报告
* **受影响的源头**: `REDIS:user-cache`
* **受影响的终点**: 
  - `org.openjtrace.example.mybatis.UserCacheService#getUserCached`
* **影响链条**:
  `REDIS:user-cache` ➜ `UserCacheService.getUserCached()`

---

## 🍃 Scenario 4: MongoDB 依赖与实体映射分析

当对 MongoDB 实体（如添加、删除字段或改变集合名）进行重构时，需要了解哪些 Repository 和 Service 会被波及。

### 示例代码
在 `mybatis-demo` 中定义了 MongoDB 的实体、Repository 接口以及 Service 服务：
```java
// User.java 实体类映射到集合 user_info
@Document(collection = "user_info")
public class User {
    private String id;
    private String name;
}

// UserMongoRepository.java
public interface UserMongoRepository extends MongoRepository<User, String> {
    List<User> findByName(String name);
}

// UserMongoService.java
@Service
public class UserMongoService {
    @Autowired
    private UserMongoRepository userMongoRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    public List<User> queryUser(String name) {
        return userMongoRepository.findByName(name);
    }

    public void removeUser(String id) {
        mongoTemplate.remove(id, User.class);
    }
}
```

### 运行 OpenJTrace 扫描
分析修改了 `MONGO:user_info`（例如对应的 Collection 名称或实体属性变动）对上游方法的影响：
```bash
java -jar openjtrace-cli.jar \
  -dirs ./mybatis-demo \
  -target "MONGO:user_info" \
  -output ./mongo-report.html
```

### 追踪分析链路
1. **Repository 接口泛型推导**：`analyzer-mongodb` 识别到 `UserMongoRepository` 继承自 `MongoRepository<User, String>`。获取其泛型类 `User` 上的 `@Document(collection = "user_info")`，提取出 `MONGO:user_info` 集合节点。
2. **Repository 方法映射边**：在 Repository 接口中，所有方法（例如 `findByName`）都会被映射至该集合，建立 `UserMongoRepository#findByName ──(MAPS_TO)──> MONGO:user_info` 的关联。
3. **MongoTemplate 显式类型匹配**：分析 `UserMongoService#removeUser` 时，检测到 `mongoTemplate.remove` 调用并传入了 `User.class` 字面量。通过 `User` 类上的 `@Document` 注解找到集合 `user_info`，建立调用边 `UserMongoService#removeUser ──(CALLS)──> MONGO:user_info`。
4. **Service 方法关联**：普通的静态方法调用分析器（`parser-java`）识别出 `UserMongoService#queryUser` 内部调用了 `UserMongoRepository#findByName`。

### 最终输出的受影响报告
* **受影响的源头**: `MONGO:user_info`
* **受影响的终点**: 
  - `org.openjtrace.example.mybatis.UserMongoService#queryUser`
  - `org.openjtrace.example.mybatis.UserMongoService#removeUser`
* **影响链条**:
  1. `MONGO:user_info` ➜ `UserMongoRepository.findByName()` ➜ `UserMongoService.queryUser()`
  2. `MONGO:user_info` ➜ `UserMongoService.removeUser()`
