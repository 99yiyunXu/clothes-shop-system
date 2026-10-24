# LUNA 服装商店项目

保留原有 `com.example.demo` 包与 Controller、Service、Repository、Entity 分层，保留 `ProuductService` 类名、原有图片、登录页、首页和商品列表的布局。在原结构中新增购物功能。代码、页面与 SQL 的新增注释使用日语；这份操作说明使用中文。

## 已完成的功能

| 模块 | 功能 |
| --- | --- |
| 用户 | 数据库注册、BCrypt 密码哈希、登录、退出、30 分钟会话、普通用户与管理员权限 |
| 登录保护 | CSRF 校验、登录时会话保护、同一 IP 5 分钟内最多 10 次登录尝试 |
| 首页 | 最多 4 个新着商品、数据库随机推荐、随机横幅、活动入口和分类入口 |
| 商品 | 名称搜索、分类搜索、组合搜索、SALE 筛选、每页 12 件、详情、库存展示 |
| 收藏 | 添加、解除、个人收藏列表与分页 |
| 购物车 | 数据库保存、数量更新、删除、后台计算金额、库存与数量校验 |
| 订单 | 地址确认、订单与明细保存、历史价格快照、防重复下单、本人订单列表与详情 |
| 库存 | 下单事务扣减、库存不足整单回滚、取消与退货恢复库存、并发购买保护 |
| 模拟支付 | 明确标记为学习用演示，没有银行卡输入、实际扣款或实际退款 |
| 配送与退货 | 管理员登记发货和追踪编号、用户确认收货、申请退货、管理员确认退货 |
| 管理页面 | 新增与编辑商品、检查图片路径、库存设置、SALE 标记、销售结束、分页与订单管理 |

真实支付渠道、物流公司接口和自动退款仍需要外部服务的商户配置。当前发货与退货由管理员手动确认。推荐商品为随机推荐，不是个性化推荐。商品图片兼容现有 `static/images` 文件，管理页面也支持上传、预览和确认关联。

## 启动前准备

需要 JDK 25、MySQL 8.0.16 或更新版本。保留项目原有 Spring Boot 4.1.1 配置。测试只使用 H2 内存数据库，不连接 MySQL。

### 1. 备份并检查原数据库

先备份 `luna_shop`。在 MySQL 客户端中检查现有表：

```sql
SHOW CREATE TABLE luna_shop.products;
```

原商品表应使用 InnoDB，ID 为有符号 `INT`，具有 `id / name / price / image_url / category` 列。如果实际字段不同，先核对原表与 `Product.java` 的映射，不要直接执行升级脚本。已有价格应为非负整数，名称不能为空，图片地址应类似 `/images/products/knit1.png`。历史分类与页面分类值需一致：`レディース`、`メンズ`、`アクセサリー`。

### 2. 手动执行升级脚本

使用有建表、修改表和创建存储过程权限的数据库管理账号执行：

`src/main/resources/db/upgrade.sql`

脚本保留既有商品，添加描述、库存、销售状态、SALE 标记、注册时间、版本号，以及用户、购物车、收藏、订单和订单明细表。既有商品的库存默认是 **0**，必须在管理页面确认真实库存后才能购买。旧商品原先没有注册时间，脚本使用升级时间，因此旧商品之间会按 ID 排序。

脚本不包含删除表或清空商品操作；MySQL 的结构变更无法当作普通事务整体回滚，因此仍应先备份。脚本可再次执行来补齐未添加的字段，但不会自动纠正已存在字段的类型或内容。若数据库尚不存在，应先手动创建 `luna_shop`。可选的 `demo-products.sql` 只在需要示例商品时手动执行，不会覆盖同图片路径的已有商品。

程序配置为 `ddl-auto=validate` 和 `spring.sql.init.mode=never`，只校验表结构，不会自动执行这些 SQL 文件。

### 3. 设置数据库账号与环境变量

建议为应用创建专用账号，而不是使用 root。由数据库管理员手动执行下列示例，并自行替换密码：

```sql
CREATE USER 'luna_app'@'localhost' IDENTIFIED BY '请替换为你自己的数据库密码';
GRANT SELECT, INSERT, UPDATE, DELETE ON luna_shop.* TO 'luna_app'@'localhost';
```

在 Eclipse：`Run → Run Configurations → Java Application → 当前启动配置 → Environment`，添加：

| 环境变量 | 示例 |
| --- | --- |
| `DB_USERNAME` | `luna_app`，或者你已有的专用账号 |
| `DB_PASSWORD` | 该数据库账号的实际密码，必填 |
| `DB_URL` | 可选；默认连接本机 3306 的 `luna_shop` |

配置文件不再保存原明文密码。正式 HTTPS 部署时设置 `SESSION_COOKIE_SECURE=true`。旧源码历史中出现过的数据库密码应自行更换。

### 4. Eclipse 更新并启动

1. 右键项目，选择 `Maven → Update Project`。
2. 检查项目 JRE 与运行配置使用 JDK 25。
3. 配好环境变量后，运行 `ClothesShopSystemApplication`。
4. 浏览器访问 `http://localhost:8080/`。
5. 点击新用户注册，使用 3～50 个英数字或下线作为用户名，8～64 字符作为密码。日语等多字节密码还需在 UTF-8 的 72 字节以内，超长时页面会提示缩短。

原来写在代码中的 5 个演示账号已被移除，不会自动迁移成数据库用户。所有新注册用户均为普通用户；用户名叫 `admin` 也不会自动获得管理员权限。

### 5. 设置第一个管理员

先在注册页面创建你自己的账号。再由数据库管理员执行以下示例，把 `your_registered_username` 替换成实际已注册用户名：

```sql
UPDATE luna_shop.shop_users
SET role = 'ADMIN'
WHERE username = 'your_registered_username';
```

确认只修改了目标账号，然后退出并重新登录。首页和各新页面会出现“商品管理”和“订单管理”。管理账号可修改商品库存。管理员权限不允许普通用户通过注册表单自行设置。

## 初学者可以按这个顺序验证

1. 用户注册、正确与错误密码登录、退出、未登录访问首页。
2. 首页横幅与分类，商品列表搜索、SALE 筛选、分页、详情。
3. 管理员修改库存，再以普通用户加入购物车、修改数量、收藏与解除收藏。
4. 填写配送地址并下单；订单时再次读取最新价格，商品合计满 10,000 日元免运费，否则送料 500 日元。
5. 在订单详情取消未付款订单，确认库存恢复。
6. 新建订单并完成“デモ支払い”，管理员登记追踪编号并发货，用户确认收货。
7. 用户填写退货理由，管理员收到退货后确认，确认库存恢复与状态变更。
8. 换另一个账号，确认无法查看或操作他人的订单、购物车与收藏，也不能打开管理页面。

订单状态：`支払い待ち → 支払い済み → 発送済み → 受取済み`；未付款订单可以取消；发货后可以申请退货，由管理员确认。未付款订单在取消前会保留库存，本版没有自动超时取消任务。

管理员编辑页面携带商品版本号。如果编辑期间有下单或其他管理员修改，保存会提示重新打开页面，避免旧库存覆盖最新库存。商品“删除”采用销售结束方式，保留订单历史。

## 测试

```powershell
.\mvnw.cmd -B -ntp test
```

测试配置位于 `src/test/resources/application-test.properties`，使用 `jdbc:h2:mem:luna_test`，不会读取或修改你的 MySQL 数据。覆盖登录权限、CSRF、Thymeleaf 页面渲染、搜索分页、收藏、购物车、订单归属、重复下单、价格快照、事务回滚、状态流转、并发库存和过期商品编辑。

本次已编译并使用内存数据库验证业务流程；没有实际连接你的 MySQL、没有执行升级脚本。H2 测试不替代 MySQL 的表结构与连接验证，执行升级后仍需按上面的流程验证。

## 商品图片管理

保留原有画面布局、样式和图片文件。商品图片未设置、路径不存在或浏览器加载失败时，统一显示 3:4 的 `NO IMAGE` 占位图。新增或编辑商品时允许图片栏留空，填写错误路径仍会提示修正。占位处理不会改写已有商品或历史订单的图片路径。

图片规范、命名规则和 AI 图片批量审核流程见 [商品画像管理](docs/商品画像管理.md)，原图片台账见 [product-image-inventory.csv](docs/product-image-inventory.csv)，后续导入清单使用 [image-import-template.csv](docs/image-import-template.csv)。后台支持单张上传及确认关联，不自动生成图片、批量关联或增加商品。持久化目录和操作步骤见 [商品图片上传](docs/商品图片上传.md)。

上传默认保存在应用工作目录的 `data/product-images`，不会被 Maven 构建清理。建议在 Eclipse 环境变量中设置固定绝对路径 `LUNA_IMAGE_DIR=D:/Eclipse/clothes-shop-system/data/product-images`，以后重启和部署使用同一路径，并单独备份该目录。数据库仅保存 `/images/uploads/...` 路径。每次上传1张、最大5 MB，每个登录会话最多20张，目录默认最多2000个文件。

只读核对原图片是否被改动：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/audit-product-images.ps1
```

该命令的执行策略仅对当前子进程有效，不改变系统设置；脚本不连接数据库、不写文件。JavaScript 兜底逻辑可用 `node --test tools/product-images.test.cjs` 验证。项目注释规范见 [コメント規約](docs/コメント規約.md)。

## 文件职责

`Controller` 接收页面请求；`Service` 校验业务规则与控制事务；`Repository` 查询数据库；`Entity` 映射数据库表。`SecurityConfig` 位于原来的 controller 包，统一保护入口；`PageAdvice` 提供当前用户和管理员标记。原有 `ProuductService` 名称保留，兼容原项目结构。

新页面放在原 `templates` 目录，通用导航放在 `templates/fragments/shop.html`，错误页放在 `templates/error`，新增样式放在 `static/css/shop.css`。原来的登录、首页和商品列表页面继续作为入口。

Windows Maven Wrapper 有一处对空链接目标进行数组访问的问题，本次只修正该空值检查，未替换 Wrapper 或项目构建方式。

登录表单与 CSRF 的接入依据 [Spring Security 表单登录文档](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)，数据库行锁使用 [Spring Data JPA 锁文档](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) 中的声明方式。
