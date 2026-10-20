# 精简商城

实践顺序一：商品新增、修改、分类筛选、排序和分页。后端采用 Spring Boot、MyBatis-Plus、MySQL，前端采用 Vue 3。

数据库使用全新 test_shop 基线，完整建库脚本定义 13 张表。所有表仅有逻辑关联，不创建物理外键；当前业务代码已实现商品模块，其余模块按实践顺序接入。

## 全项目执行文档

按九个实践阶段开发时，从 [test_shop 全项目执行总册](docs/superpowers/plans/2026-10-20-test-shop-execution.md) 开始。总册关联完整表结构，并链接九份阶段清单，逐项说明字段映射、接口、SQL、事务、并发、缓存、消息、权限、部署与通知服务拆分；清单描述后续实施，不表示功能已完成。

## 目录

- src/main/java/com/chengli/shop/controller：商品、分类接口。
- src/main/java/com/chengli/shop/service：业务接口及 impl 实现。
- src/main/java/com/chengli/shop/mapper：继承 MyBatis-Plus BaseMapper 的数据访问接口。
- src/main/java/com/chengli/shop/config：分页拦截器与时间字段自动填充。
- src/main/java/com/chengli/shop/entity、dto、vo：数据库实体、请求参数、响应对象。
- src/main/java/com/chengli/shop/enums：商品、订单、库存、账号和消息的状态编码。
- docs/design/java-data-classes.md：13 张表的实体映射及 DTO、VO、枚举说明。
- src/main/java/com/chengli/shop/common、exception：统一响应、分页、异常处理。
- src/main/resources/sql/test_shop.sql：test_shop 全量建库脚本，包含 13 张表及分类、角色字典。
- docs/design/test-shop-data-model.md：当前完整模型、字段说明与逻辑关系图。
- frontend/src/views/Storefront.vue：商城首页，提供商品搜索、分类、价格排序、分页和详情浏览。
- frontend/src/storefront.css：京东风格的红白配色、分类导航、轮播主视觉与响应式商品网格。
- frontend/src/views/ProductManagement.vue：商品管理页面。
- frontend/src/components/ProductEditor.vue：商品新增和修改表单。

## 分层职责

- Controller：接收和校验请求参数，输出统一 HTTP 响应。
- Service：处理业务规则、事务、查询条件和实体到 VO 的组装。
- Mapper：仅继承 BaseMapper，操作数据库实体，商品查询全部使用 MyBatis-Plus。
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

当前商品接口支持基础字段、subtitle 短卖点和单张主图；完整图片集合的编辑、库存订单、权限及消息接口仍按实践顺序开发。相关实体、DTO、VO 和状态枚举见 [Java 数据类](docs/design/java-data-classes.md)。建表不代表这些功能已实现，也不会自动生成可登录账号。

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

没有在售商品或商品服务不可用时，首页明确标记并展示九件示例商品与示例价格。示例图使用 AI 生成的商品示意图，仅用于展示设计，不支持购买，也不会写入数据库。发布真实在售商品后，返回首页或点击“重新加载商品”即可加载实际商品。

点击“新增商品”打开表单；列表行内“编辑”可修改该商品。名称、分类与价格必填，保存期间按钮禁用。新增后重置筛选并显示第一页；修改后保留筛选，重新载入当前页。

新商品默认下架，可先保存资料；补齐主图并选择上架后，商品才会出现在商城首页。

分类、状态和名称通过“查询”按钮应用，切换排序或每页条数时回到第一页。列表包含加载中、空列表和请求失败状态，失败时可重新加载。

## 迁移回归

本地 MySQL 初始化完成后，在项目根目录执行：

    mvn verify

测试会启动随机端口的真实服务，覆盖商品增改、分类状态筛选、分页总数、排序、关键词转义与 HTTP 响应。测试新增的分类和商品在事务结束后回滚，HTTP 用例只读取数据。

## 技术资料

- [MyBatis-Plus 官方安装说明](https://baomidou.com/getting-started/install/)
- [MyBatis-Plus 分页插件](https://baomidou.com/plugins/pagination/)
- [Vue 官方快速开始](https://vuejs.org/guide/quick-start.html)
- [Vite 开发服务器与代理](https://vite.dev/config/server-options.html)
