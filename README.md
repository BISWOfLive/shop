# 精简商城

实践顺序一：商品新增、修改、分类筛选、排序和分页。后端采用 Spring Boot、MyBatis-Plus、MySQL，前端采用 Vue 3。

数据库使用全新 test_shop 基线，完整建库脚本定义 13 张表。所有表仅有逻辑关联，不创建物理外键；当前已实现商品查询、库存维护及开发环境的订单本地事务；权限、消息等后续模块按实践顺序接入。

## 开发文档

本 README 是项目统一入口。当前只组织商品、查询优化、库存订单三个阶段；第 4～9 阶段暂不展开。设计和计划描述目标，不代表业务已经实现。

| 阅读顺序 | 文档 | 内容 |
| --- | --- | --- |
| 1 | [数据模型](docs/design/test-shop-data-model.md) | 13 张表、逻辑关联、字段和业务约束 |
| 2 | [Java 数据类](docs/design/java-data-classes.md) | Entity、DTO、VO 与枚举的对应关系 |
| 3 | [核心设计](docs/superpowers/specs/2026-10-20-core-shop-design.md) | 前三个阶段的架构、接口契约和 BaseMapper 边界 |
| 4 | [阶段 1：商品](docs/superpowers/plans/test-shop/01-products.md) | 商品资料、图片集合、Vue 表单、筛选排序分页 |
| 5 | [阶段 2：指定查询优化](docs/superpowers/plans/test-shop/02-query-optimization.md) | 从纯 BaseMapper 起点新增两条固定游标查询 |
| 6 | [阶段 3：库存与订单](docs/superpowers/plans/test-shop/03-inventory-orders.md) | 库存准备、下单事务、快照、主动取消与模拟支付 |

实施对应任务时回填该阶段分册；跨阶段的结构或文档改动集中记录在阶段 1 末尾。已删除的总册和历史计划不再作为入口，也不恢复。

## 目录

工作区外层保存协作规则，内层 `shop/` 是 Maven 工程根。下列源目录保持原位：

```text
工作区/
├─ AGENTS.md
└─ shop/
   ├─ README.md                     项目与开发文档入口
   ├─ pom.xml                       后端依赖与构建声明
   ├─ src/
   │  ├─ main/
   │  │  ├─ java/com/chengli/shop/   后端代码
   │  │  └─ resources/
   │  │     ├─ application.yaml     应用配置
   │  │     └─ sql/test_shop.sql    完整建库基线
   │  └─ test/java/                 现有测试源码
   ├─ frontend/
   │  ├─ src/
   │  │  ├─ api/                    HTTP 接口封装
   │  │  ├─ components/             表单、轮播和商品展示组件
   │  │  ├─ composables/            首页数据和生命周期逻辑
   │  │  ├─ data/                   前端静态映射
   │  │  ├─ views/                  商城和商品管理页面
   │  │  ├─ App.vue                 页面入口与 hash 切换
   │  │  ├─ main.js                 Vue 挂载入口
   │  │  ├─ styles.css              管理界面样式
   │  │  └─ storefront.css          商城样式
   │  └─ public/product-images/     商品静态图片和目录
   ├─ docs/
   │  ├─ design/                   数据模型与 Java 数据类
   │  └─ superpowers/
   │     ├─ specs/                  三个核心阶段的总设计
   │     └─ plans/test-shop/        阶段 1、2、3 执行计划
   ├─ scripts/                     商品数据生成器、价格和图片配置
   └─ data/generated/              生成数据与素材产物
```

后端包职责：

| 包 | 职责 |
| --- | --- |
| controller | HTTP 请求、参数校验、统一响应 |
| service / service.impl | 业务规则、事务和响应组装 |
| mapper | BaseMapper 数据访问，当前没有商品查询 XML |
| entity | 数据库表字段映射 |
| dto / vo | 请求参数与对外响应 |
| enums | 商品、库存、订单、账号和消息的状态编码 |
| config | 分页插件与时间自动填充 |
| common / exception | 通用响应、分页结构和异常处理 |

阶段计划中的 Java 简写路径 `controller/`、`service/`、`mapper/`、`dto/`、`vo/`、`entity/`、`enums/`、`config/`、`identity/` 均以 `src/main/java/com/chengli/shop/` 为前缀；其他路径相对内层工程根。同一清单中连续列出的裸文件名沿用该行注明的包目录。

`target/`、`frontend/node_modules/`、`frontend/dist/`、`frontend/.npm-cache/` 属于本地生成目录；`.idea/`、`*.iml` 属于 IDE 文件，不计入业务代码层次。[scripts/README.md](scripts/README.md) 单独说明造数和图片配置。

## 分层职责

- Controller：接收和校验请求参数，输出统一 HTTP 响应。
- Service：处理业务规则、事务、查询条件和实体到 VO 的组装。
- Mapper：业务读写使用 BaseMapper；指定商品 feed 和数据库时钟保留固定注解 SQL，不新增 XML。
- entity 对应数据库字段，dto 只承载请求参数及校验规则，vo 承载前端需要的响应字段。

商品列表先调用 BaseMapper.selectPage 分页，再将当前页的分类 ID 去重，一次批量获取分类名称后组装 ProductVO。商品详情使用 selectById。当前商品查询不需要 XML，也不需要逐条查询分类。

分类采用逻辑关联；若历史商品引用的分类已不存在，读取时保留商品并显示“分类不存在”，新增和修改仍要求分类有效。

## 启动

### 1. 数据库初始化

使用 Java 21、Maven、MySQL 8.0.16 或更高版本，以及 Node.js 22.18 或更高版本。

在 MySQL 客户端执行 [test_shop.sql](src/main/resources/sql/test_shop.sql)，创建 test_shop 数据库及全部 13 张表，并写入四个分类、ADMIN 和 USER 两个角色。目标库可以尚未创建，或已经存在但为空；表不使用 IF NOT EXISTS，重复导入将报错，不能把它当作增量升级脚本。

脚本不执行删库或删表，应用配置禁止启动时自动执行初始化 SQL。旧 init.sql 已移除，旧设计草案不再作为建库入口。完整模型见 [test_shop 数据模型](docs/design/test-shop-data-model.md)。

### 2. 启动后端

在 [application.yaml](src/main/resources/application.yaml) 的 spring.datasource 中配置本机 MySQL 连接地址、username 和 password，库名为 test_shop。应用连接初始化使用 +08:00 时区，与当前 Asia/Shanghai 业务约定一致。

在当前项目根目录的 PowerShell 中执行：

    mvn spring-boot:run

默认后端地址为 http://127.0.0.1:8080。IDE 启动时使用同一份 datasource 配置。

### 3. 启动前端

另开一个终端，在项目根目录执行：

    cd frontend
    npm install --no-audit --no-fund --update-notifier=false
    npm run dev

打开 http://127.0.0.1:5173。Vite 将 /api 请求转发到本机 8080 端口。

后端使用其他地址时，在启动前端的终端里设置 SHOP_API_TARGET，例如：

    $env:SHOP_API_TARGET = 'http://127.0.0.1:8081'
    npm run dev

## 数据模型

完整结构以 [test_shop.sql](src/main/resources/sql/test_shop.sql) 为准：

| 模块 | 表 |
| --- | --- |
| 商品展示 | category、product、product_image |
| 库存 | inventory、inventory_log |
| 订单 | shop_order、order_item |
| 账号与权限 | user_account、role、user_role |
| 消息与通知 | outbox_event、message_consume_record、notification |

product 保存标题、短卖点、分类、当前售价、主图、描述与上下架状态；product_image 保存有序附图和详情图。库存独立，订单明细保存下单名称、主图、单价和数量快照。

所有关联只保留 ID 和必要索引，由服务层校验关联存在及归属；数据库保留主键、唯一键与 CHECK。商品当前售价使用 DECIMAL(12,2)，Java 使用 BigDecimal；订单金额使用 DECIMAL(18,2)。商品默认下架，上架必须有主图。

当前商品接口支持基础字段、subtitle 短卖点和单张主图；库存订单已按阶段 3 接入，完整图片集合的编辑、权限及消息接口仍按实践顺序开发。相关实体、DTO、VO 和状态枚举见 [Java 数据类](docs/design/java-data-classes.md)。建表不代表这些功能已实现，也不会自动生成可登录账号。

## 接口

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | /api/categories | 查询所有分类 |
| GET | /api/products | 商品筛选、排序和分页 |
| GET | /api/products/{id} | 查询商品详情 |
| POST | /api/products | 新增商品 |
| PUT | /api/products/{id} | 修改商品 |

商品列表查询参数：

| 参数 | 默认值 | 规则 |
| --- | --- | --- |
| keyword | 无 | 商品名称包含查询，最多 120 个字符 |
| categoryId | 无 | 按分类 ID 筛选 |
| status | 无 | 0 下架，1 上架 |
| page | 1 | 从 1 开始 |
| size | 10 | 1–100 |
| sortBy | createdAt | createdAt、price、name |
| sortOrder | desc | asc、desc |

分页使用 BaseMapper.selectPage、Page 和 PaginationInnerInterceptor 自动生成总数查询与 LIMIT/OFFSET。Service 使用 LambdaQueryWrapper 处理分类、状态筛选及固定字段排序；名称搜索使用参数绑定并转义 LIKE 通配符。排序值相同时追加商品 ID 降序，使相同数据集下的分页次序固定。

依赖使用适配 Spring Boot 4 的 mybatis-plus-spring-boot4-starter 3.5.17，分页模块 mybatis-plus-jsqlparser 保持同版本。实体配置自增主键，查询、新增和修改均由 BaseMapper 完成；创建和更新时间通过 MetaObjectHandler 自动填充。

新增和修改使用相同的请求体，必填字段为 name、categoryId、price，status 默认 0（下架）：

    {
      "name": "桌面收纳盒",
      "categoryId": 2,
      "price": 39.90,
      "description": "适合桌面文具收纳",
      "imageUrl": "",
      "status": 0
    }

categoryId 使用分类接口返回的实际 ID。下架时可暂不填写主图，上架时 imageUrl 必填；填写时须以 http:// 或 https:// 开头。名称最多 120 字符，描述最多 2000 字符，图片地址最多 512 字符。

响应格式：

    {
      "code": 0,
      "message": "操作成功",
      "data": {
        "records": [],
        "total": 0,
        "page": 1,
        "size": 10
      }
    }

code 为 0 表示成功。新增成功返回 HTTP 201；参数错误返回 400，商品不存在返回 404，数据库约束冲突返回 409，服务错误返回 500；失败时 data 为 null。

## 页面操作

默认打开商城首页，采用搜索框、分类导航、好物推荐和商品卡片布局。首页只展示上架商品，商品查询继续使用现有后端接口；点击商品卡片打开详情。点击右上角“商品管理”进入 `#manage`，可以继续使用原有新增、修改和筛选功能；管理页可返回商城首页。

点击“新增商品”打开表单；列表行内“编辑”可修改该商品。名称、分类与价格必填，保存期间按钮禁用。新增后重置筛选并显示第一页；修改后保留筛选，重新载入当前页。

新商品默认下架，可先保存资料；补齐主图并选择上架后，商品才会出现在商城首页。

分类、状态和名称通过“查询”按钮应用，切换排序或每页条数时回到第一页。列表包含加载中、空列表和请求失败状态，失败时可重新加载。

## 迁移回归（仅在当轮明确授权后执行）

本地 MySQL 初始化完成后，在项目根目录执行：

    mvn verify

测试会启动随机端口的真实服务，覆盖商品增改、分类状态筛选、分页总数、排序、关键词转义与 HTTP 响应。测试新增的分类和商品在事务结束后回滚，HTTP 用例只读取数据。

## 技术资料

- [MyBatis-Plus 官方安装说明](https://baomidou.com/getting-started/install/)
- [MyBatis-Plus 分页插件](https://baomidou.com/plugins/pagination/)
- [Vue 官方快速开始](https://vuejs.org/guide/quick-start.html)
- [Vite 开发服务器与代理](https://vite.dev/config/server-options.html)

## 查询性能改动（2026-10-20）

阶段 2 已实施；此前授权运行记录为后端 42 项、前端 13 项及生产构建通过，真实 test_shop 库 500 万商品的查询计划和同口径性能样本已归档。这些是新增测试删除前的历史结果。全部变更、索引设计理由、测试清理记录与覆盖边界见 [阶段 2 执行计划](docs/superpowers/plans/test-shop/02-query-optimization.md)，具体数据及原始样本见 [性能记录](docs/performance/02-query-results.md)。

新增 `GET /api/products/feed`：全站最近新增使用 `sortBy=createdAt` 且不传分类；分类低价使用 `sortBy=price&categoryId=实际分类ID`。size 默认 20、范围 1～100；后续原样回传 cursor。返回 records/nextCursor/hasMore，不查询总数，商品卡片的 ID 和金额使用字符串。页面及抽屉商品流每批 24 条，提前取下一批并按可视区域渲染；其他查询仍走普通分页。

普通 `GET /api/products` 新增可选 `includeTotal`，默认 true 保留精确总数；false 时关闭 COUNT，total=-1 表示未统计。首页低价推荐与商品流后续批次使用该选项；管理端默认分页、查询和跳页能力保持原样。全站价格排序、关键词和其他非 feed 组合仍可能有排序或深偏移成本。

建库基线已同步全站价格和分类上新两条补充索引；已有库的一次性脚本见 [增量索引 SQL](src/main/resources/sql/migrations/2026-10-20-product-query-indexes.sql)。用户已执行的实际索引已核验并在对应执行计划中采用，因此本轮没有重复建索引。索引创建前未采集基线，不填写创建前后提升倍数。

在此次本机暖缓存、单客户端实验中，偏移 24 万条时，分类低价的数据访问与分类装配中位数为 OFFSET 459.374ms、已知游标 2.488ms；全站上新为 417.163ms 与 1.159ms。相同工作量、均不 COUNT；这些不是浏览器首屏或生产并发指标。

按用户后续要求，本次新增的 5 个测试源码文件及 `npm test` 入口已移除，性能实验用例不再随仓库保留；原有后端测试保留。前端构建入口仍为 `npm run build`，执行须遵循当轮授权。此前同时修复了抽屉触底后因滚动锚定而连锁加载的问题；本次清理未运行验证。

## 库存与订单（2026-10-20）

阶段 3 的库存、下单、本人订单查询、主动取消、模拟支付及 Vue 流程已实现。新增商品与零库存同事务提交；下单扣库存并保存名称、图片、价格快照，取消归还库存，支付不再扣减。订单过期后拒绝支付，本阶段不会自动取消。

交易及库存维护接口只在显式 dev profile 装配。先按 [数据准备说明](scripts/README.md#库存与开发买家准备阶段-3) 手工准备专用禁用占位买家和所需存量商品的库存，在本机终端设置实际买家 ID：

```powershell
$env:SHOP_DEV_BUYER_ID = '实际占位买家ID'
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

无配置或账号不存在时订单接口返回 503，没有默认买家或请求身份覆盖。库存人工维护的 actor_id 为空，开发买家不充当管理员。本功能用于本地练习，尚未接入真实登录与授权。

| 方法和路径 | 作用 |
| --- | --- |
| GET /api/inventories/{productId} | 商品库存，库存行缺失返回 409 |
| POST /api/inventories/stock-in | 入库；productId、bizNo、quantity、remark |
| POST /api/inventories/adjust | 有符号调整；productId、bizNo、changeQty、remark |
| GET /api/inventory-logs | 按 productId、orderId、changeType、bizNo 分页查流水 |
| POST /api/orders | 商品 ID、数量和 idempotencyKey；新建 201，同意图重试 200 |
| GET /api/orders | 当前买家订单摘要分页 |
| GET /api/orders/by-request?idempotencyKey=请求号 | 当前买家按原请求号恢复订单，支持所有可见 ASCII |
| GET /api/orders/by-request/{idempotencyKey} | 保留单段路径兼容入口，UUID 等安全请求号可用 |
| GET /api/orders/{orderNo} | 本人订单详情与成交快照 |
| POST /api/orders/{orderNo}/cancel | 主动取消待支付订单 |
| POST /api/orders/{orderNo}/mock-pay | 截止时间前模拟支付 |

商品、库存和订单响应中的 ID、金额使用字符串，库存/订单时间携带 +08:00。订单请求不能指定身份、价格、快照、状态或时间；同请求号携带不同商品数量返回 409。库存业务号的数量或规范化备注变化同样返回 409。

页面入口：#inventory 库存维护、#checkout 结算、#orders 本人订单、#orders/{orderNo} 详情。商品详情可立即购买或加入内存采购清单；清单支持合并、改数量和移除。清单与未完成结算只在当前页面进程保存，刷新页面会丢失；没有写入数据库或 localStorage。创建结果未知时先按原请求号恢复，再允许沿用原请求重试。

### 本阶段测试

仅在当轮明确授权时执行：

```powershell
mvn clean verify
cd frontend
npm test
npm run build
```

后端测试连接当前配置的 MySQL；商品回归数据事务回滚，交易用例按唯一测试买家和商品创建数据并在结束后定向清理。SQL 模板验收只针对测试商品单个 ID 区间，不对存量商品批量写数据。

浏览器验收使用 Chromium、已构建的 frontend/dist 和临时代理，真实调用测试服务：

```powershell
# 先执行 npm run build，再回到后端根目录
$env:SHOP_BROWSER_TEST = '1'
# Playwright 不在常规 node_modules 时指定其所在依赖目录
$env:SHOP_BROWSER_NODE_MODULES = '实际包含playwright包的node_modules目录'
mvn clean verify
```

SHOP_BROWSER_TEST 未设置时浏览器用例跳过；全量验收须显式启用，并准备可用的 Playwright Chromium。本次实际结果、执行原因、全部变更文件和剩余数据准备见 [阶段 3 计划回填](docs/superpowers/plans/test-shop/03-inventory-orders.md)。
