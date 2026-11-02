# 精简商城

基于 Spring Boot、MyBatis-Plus 和 Vue 3 的商城项目，包含商品管理、库存、订单、Session 登录与权限、商品详情缓存、订单到期取消及独立通知服务。

## 技术栈

- 后端：Java 21、Spring Boot 4.1.1、MyBatis-Plus 3.5.17、MySQL 8.0.16+。
- 前端：Vue 3.5.22、Vite 7.1.7、Node.js 22.18+。
- 配套服务：Redis、RabbitMQ、Docker Compose。

## 目录

```text
.
├─ compose.yaml                 完整后端 Docker 入口，内置镜像构建
├─ .env.example                 空值配置模板
├─ pom.xml                      商城后端依赖
├─ src/main/java/               商城业务代码
├─ src/main/resources/          应用配置与商城 SQL
├─ notification-service/        独立通知工程与通知 SQL
├─ frontend/                    Vue 页面、前端配置与示例图片
├─ docs/contracts/              服务间事件协议
└─ deploy/operations.md         启动与日常操作说明
```

## 使用 Docker 启动后端

使用 Docker Desktop 的 Linux 容器模式和 Docker Compose 2.17+。MySQL 保留在本机 3306，其他后端服务运行在 Docker 中。

1. 在同一个 MySQL 实例中准备两个库。新库分别执行：
   - [商城基线](src/main/resources/sql/test_shop.sql)：创建 test_shop 和 13 张表。
   - [通知基线](notification-service/sql/test_shop_notification.sql)：创建 test_shop_notification 和两张表。
   已有完整表时跳过对应脚本；这些脚本不用于重复初始化或增量升级。
2. 在仓库根目录复制配置模板并填写 MySQL、RabbitMQ 账号密码：

   ```powershell
   if (-not (Test-Path -LiteralPath '.env')) {
       Copy-Item -LiteralPath '.env.example' -Destination '.env'
   }
   notepad .env
   ```

3. 构建并启动：

   ```powershell
   docker compose up -d --build
   ```

启动四个常驻服务：shop-api、shop-notification、redis、rabbitmq。notification-keys 是一次性密钥任务，完成后 Exited (0) 属于正常状态。通知密钥运行时生成并按公私钥分别挂载，不包含在源码或镜像中。

- 商城 API：http://127.0.0.1:8080/api/categories。
- RabbitMQ 管理页：http://127.0.0.1:15672，使用自己填写的账号密码。
- 容器访问本机 MySQL 使用 host.docker.internal:3306；两个逻辑库共用同一个本机 MySQL 服务。
- 当前入口仅绑定本机 HTTP，为登录 Cookie 明确覆盖 Secure=false；公网部署需要单独配置 HTTPS 入口。

完整步骤、端口与日志说明见 [部署操作说明](deploy/operations.md)。

## 启动前端

在 frontend 目录执行：

```powershell
npm ci
npm run dev
```

访问 http://127.0.0.1:5173。默认 /api 请求代理到本机 8080；后端使用其他入口端口时，可设置 SHOP_API_TARGET。

## 原生启动后端

使用 Java 21 和 Maven，在仓库根目录设置本机环境变量：

```powershell
$env:SHOP_DB_URL = 'jdbc:mysql://127.0.0.1:3306/test_shop?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:SHOP_DB_USERNAME = '你的数据库账号'
$env:SHOP_DB_PASSWORD = '你的数据库密码'
$env:SPRING_PROFILES_ACTIVE = 'dev'
mvn spring-boot:run
```

原生启动的缓存、订单到期和通知开关默认关闭；完整配套服务由 Compose 入口管理。仓库不会提供默认登录账号或默认口令，普通账号可以注册，首名管理员通过 bootstrap-admin 独立入口显式创建。

## 配置与数据边界

- application.yaml 是公共配置，dev/prod 文件按激活环境覆盖；bootstrap-admin 用于首名管理员初始化。
- 数据库密码、MQ 密码、签名私钥由本机配置或密钥卷提供，不写入源码；只提交空值 .env.example。
- ID、金额通过字符串传递，订单明细保存商品与价格快照；订单、库存及流水使用本地事务。
- Session 和 CSRF 保护写接口，管理功能要求 ADMIN，订单与通知按本人归属授权。
- 通知通过独立库、Outbox、RabbitMQ 和内部签名请求协作，事件协议见 [order-notification-v1.schema.json](docs/contracts/order-notification-v1.schema.json)。

## 日常操作

```powershell
docker compose logs -f shop-api shop-notification
docker compose stop
docker compose up -d --build
```

停止与重新构建保留具名卷；MySQL 数据由原有 MySQL 服务管理。示例图片属于前端显示资源，数据库真实业务数据、备份、测试源码及内部开发记录不随运行源码提交。
