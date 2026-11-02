# 本机后端 Docker 启动说明

后端统一使用工程根目录的 [compose.yaml](../compose.yaml)。镜像构建内容已经内置，日常不需要单独运行 Dockerfile。

## 运行范围

| 服务 | 运行位置 | 地址与用途 |
| --- | --- | --- |
| MySQL | Windows 本机，保留原服务 | 3306；容器通过 host.docker.internal 访问 |
| shop-api | Docker | http://127.0.0.1:8080；商品、库存、订单、登录、通知代理 |
| shop-notification | Docker | 仅内部 shop-notification:8082；本人通知与消息消费 |
| redis | Docker | 仅内部 redis:6379；商品详情缓存 |
| rabbitmq | Docker | 仅内部 rabbitmq:5672；管理页 http://127.0.0.1:15672 |
| notification-keys | Docker 一次性任务 | 生成或复用通知签名密钥，正常退出后才启动 Java 服务 |

四个服务常驻；notification-keys 显示 Exited (0) 属于完成初始化。此入口不创建 MySQL、前端、Nginx 或监控容器。

需要 Docker Desktop 的 Linux 容器模式，以及 Docker Compose 2.17 或更高版本。Compose 使用 dockerfile_inline 内置构建；不需要在 Windows 安装 Java/Maven 来构建后端镜像。首次构建需要下载基础镜像和 Maven 依赖。

两个 Java 工程直接使用 Maven package，不额外执行 dependency:resolve 预热。它们共用 BuildKit 的 Maven 下载缓存，并锁定写入；下载/构建失败最多尝试三次，使用 -U 重新处理此前失败的依赖获取。成功下载的依赖供后续构建复用，重试耗尽仍保留 Maven 错误并退出失败。构建跳过测试，只生成产物，不代表测试通过。

## 1. 准备同一 MySQL 实例中的两个库

MySQL 继续在 Windows 本机 3306 运行。完整后端包括独立通知，因此需要两个逻辑库：

- `test_shop`：商城业务库；完整基线为 [test_shop.sql](../src/main/resources/sql/test_shop.sql)。
- `test_shop_notification`：通知库；完整基线为 [test_shop_notification.sql](../notification-service/sql/test_shop_notification.sql)。

在 Navicat 或其他 MySQL 客户端执行缺少的库的对应脚本。已有完整表的库跳过；这些脚本不是增量迁移，不能重复导入已有业务库。Compose 和 Java 服务都不会自动执行建库 SQL，也不会迁移或删除现有数据。

这仍然是同一个 MySQL 服务、同一个 3306 端口，不是第二个 MySQL 容器。容器内的 localhost 指容器本身，所以 JDBC 地址固定使用 host.docker.internal:3306。

## 2. 填写一次本机配置

在 PowerShell 执行：

```powershell
# 在仓库根目录执行
if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env'
}
notepad .env
```

填写：

```dotenv
MYSQL_USERNAME=root
MYSQL_PASSWORD='你的本机MySQL密码'
RABBITMQ_USERNAME=shop
RABBITMQ_PASSWORD='你自己设置的RabbitMQ密码'
```

root 是本机账号示例；如使用其他账号，填实际值。通知服务默认沿用该账号密码；如果已经准备了仅能访问通知库的账号，可以填写 NOTIFICATION_DB_USERNAME、NOTIFICATION_DB_PASSWORD。运行账号必须能访问各自目标库。

密码可用单引号包裹，包含 $ 或 # 时按字面量读取；不把真实 .env 提交到 Git。Compose 只把每个服务实际需要的键注入该服务，不共享注入整个 .env，也不把数据库凭据或通知私钥复制到镜像。

保留默认库名和端口即可。如果先前手动运行的商城容器占用 8080、RabbitMQ 容器占用 15672，先在 Docker Desktop 停止对应旧容器，或修改 .env 中 SHOP_HTTP_PORT、RABBITMQ_MANAGEMENT_PORT。新编排不停止或删除任何旧容器、镜像、消息或数据卷。

## 3. 一次构建并启动完整后端

仍在内层工程根目录执行：

```powershell
docker compose up -d --build
```

启动顺序由 Compose 管理：

1. 构建 Java 镜像；启动 Redis、RabbitMQ 和一次性密钥任务。
2. 密钥任务完成、RabbitMQ 健康后启动通知服务，并声明通知拓扑。
3. Redis、RabbitMQ 和通知服务健康后启动商城后端。

商城缓存、订单到期、通知发布、通知消费全部开启。签名密钥自动生成，不需要手工填写 PEM；后续启动复用已有私钥，并由该私钥导出一致的公钥。商城只挂载私钥卷，通知服务只挂载公钥卷。密钥不会写入源码、镜像或日志。

API 入口：[http://127.0.0.1:8080/api/categories](http://127.0.0.1:8080/api/categories)。后端根路径没有商城网页；Vue 前端仍是独立工程。默认端口下，前端现有 Vite /api 代理可以继续使用。

RabbitMQ 管理页：[http://127.0.0.1:15672](http://127.0.0.1:15672)，使用 .env 中的 RABBITMQ_USERNAME / RABBITMQ_PASSWORD 登录。该密码用于新数据卷首次初始化；已有持久化账号时，应在管理界面修改账号并同步 .env，不通过删除数据卷改密码。

如修改 SHOP_HTTP_PORT，前端启动前需把 SHOP_API_TARGET 设置为对应地址，例如 http://127.0.0.1:8088。

## 4. 日常操作

```powershell
# 查看应用日志
docker compose logs -f shop-api shop-notification

# 修改代码后重新构建并部署
docker compose up -d --build

# 暂停本套后端，保留容器和数据卷
docker compose stop

# 移除本套容器和网络，保留持久卷
docker compose down
```

RabbitMQ 消息、通知公私钥和两个 Java 服务的文件日志分别放在具名卷中；Redis 仅保存可重建缓存，不承担持久业务数据。普通停止与重建不删除具名卷，不使用 down -v 作为日常操作。

两个 Java 服务均以 UID/GID 10001 非 root 运行，根文件系统只读，临时目录和日志卷可写。健康检查请求实际 HTTP readiness，不以端口监听代替业务就绪。管理端口、通知端口、Redis 和 AMQP 端口不映射到 Windows；只有 API 与 RabbitMQ 管理页绑定本机 127.0.0.1。

## 本机 HTTP 与生产参数

本套编排使用 prod 的连接池、日志、管理端口和启动闸门；为本机 HTTP 明确设置 SERVER_SERVLET_SESSION_COOKIE_SECURE=false，避免浏览器无法携带登录 Cookie。它是本机 Docker 入口，没有配置公网域名、TLS 或反向代理。对公网提供服务需要另行设计 HTTPS 入口，不直接扩大当前端口绑定范围。

没有自动创建默认管理员或修改账号密码。已有管理员继续使用；全新库的首个管理员仍走 bootstrap-admin 独立初始化入口。原有原生 Java/Vue 开发方式仍可使用，四个 application YAML 的职责没有合并或删除。

## 常见启动错误

| 错误 | 处理 |
| --- | --- |
| 提示 MYSQL_PASSWORD / RABBITMQ_PASSWORD 未填写 | 在根目录 .env 填写；不要修改 YAML 中的密码占位符 |
| Unknown database / Table doesn't exist | 在本机 MySQL 导入缺少的对应基线；已有库不重复初始化 |
| Access denied for user | 使用有目标库权限、允许来自 Docker 连接的本机 MySQL 账号 |
| Connection refused / Communications link failure | 处理本机 MySQL 服务、3306 监听或 Windows 防火墙；JDBC 主机保持 host.docker.internal |
| address already in use | 停止占用入口端口的旧容器，或修改 .env 中两个入口端口 |
| 不认识 dockerfile_inline | 更新 Docker Desktop / Compose 至支持 Compose 2.17+ 的版本 |
| Premature end of Content-Length / Maven 下载中断 | 保留现有镜像与数据卷，重新执行同一启动命令；构建已配置共享依赖缓存和最多三次尝试。持续失败时处理 Maven Central 的网络连接，不使用 down -v 或清空全部缓存 |
| API 尚未创建 | 处理通知 readiness 或密钥任务的实际错误，Compose 才会启动 API |

## 发布内容

公开仓库保留运行源码、依赖锁文件、空值配置模板、数据库结构、事件协议和页面素材。测试源码、真实环境配置、备份、构建产物、生成数据、内部开发计划和旧部署材料保留在本机，不随源码提交。构建仅生成应用产物，不表示业务验收已经完成。

技术依据：[Compose 内置构建](https://docs.docker.com/reference/compose-file/build/#dockerfile_inline)、[Docker Desktop 访问本机](https://docs.docker.com/desktop/features/networking/networking-how-tos/)、[Spring Boot configtree](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.configtree)。
