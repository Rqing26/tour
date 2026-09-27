# 山海行 · 旅行社管理系统

将原 Java Servlet / JSP 课堂项目重构为 Spring Boot + React 应用。提供客户、员工、管理员三个角色，覆盖线路管理、线路预订、订单协作与人员管理。

## 直接运行

在 VS Code 打开本目录，选择 **终端 → 运行任务 → 山海行：启动**。

首次启动会构建项目；已有构建时直接启动。随后访问 [山海行](http://127.0.0.1:8080/travel/)。在运行终端按 `Ctrl+C`，或运行 **山海行：停止** 任务。

| 本地演示角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | admin | Travel@2026 |
| 员工 | employee | Travel@2026 |
| 客户 | customer | Travel@2026 |

演示数据保存在本目录的 `data/` 下，重启后保留。线路、用户、订单均为演示数据。没有支付功能，页面金额表示订单金额，不代表实际收款。应用默认只监听本机。

## 开发环境

- Java 21（本机使用 Homebrew 的 OpenJDK 21）。
- Maven 3.9+。
- Node.js 22.12+ 或兼容的更新版本，npm。
- VS Code 的 Java 扩展。

后端使用 Spring Boot 内嵌 Tomcat，无需独立下载、部署或配置全局 Tomcat。前端构建资源与后端一起打包成 `target/travel-agency.jar`。

```bash
# 完整构建：前端类型检查与打包 + 后端集成测试
./scripts/dev.sh build

# 启动已构建版本，首次会自动构建
./scripts/dev.sh run

# 停止本项目的服务
./scripts/dev.sh stop

# 前端实时开发；另一个终端同时运行后端
./scripts/dev.sh frontend

# 前端构建检查 + 后端测试
./scripts/dev.sh test
```

前端开发页面为 [http://127.0.0.1:5173/travel/](http://127.0.0.1:5173/travel/)。前端请求通过 Vite 转发到本机 8080 后端，后端采用会话登录，页面和 API 需要从同一个入口访问。

修改后端代码后重新执行构建，再重启服务。修改前端时可使用实时开发任务；若访问 8080 的打包页面，也需要重新构建并重启。

调试时先运行 **山海行：调试启动**，再在“运行与调试”中选择 **连接山海行后端调试**，调试端口为本机 5005。结束调试不会自动停止服务，需在任务终端停止。

## 项目结构

```text
frontend/                    React + TypeScript 界面
  src/                       页面、组件、类型与 API 请求
  public/images/             本地风景图片
src/main/java/cn/shanhai/travel/
  controller/                HTTP 接口
  service/                   业务规则与事务
  repository/                参数化数据库访问
  dto/                       请求与响应模型
  config/                    安全配置、环境初始化与演示数据
src/main/resources/
  db/migration/              Flyway 数据库版本迁移
  application*.yml           环境配置
  static/                    前端构建产物（自动生成）
src/test/                    后端集成测试
scripts/dev.sh               构建、运行和调试入口
.vscode/                     VS Code 任务与调试配置
data/                        本地演示数据（不提交版本库）
```

## 功能与业务规则

- **客户**：注册、搜索线路、查看详情、选择人数并预订、查看自己的订单、取消尚未完成的订单、维护个人资料与密码。
- **员工**：检索待接订单、接单、退回待接池、完成自己负责的订单、查看历史记录。
- **管理员**：查看统计和全部订单、新增及编辑线路、上下架、管理员工和客户、停用账号。
- 所有修改由后端验证角色与订单归属；按钮是否显示不作为权限依据。
- 预订时保存单价和总价快照；线路后续改价不会改变历史订单金额。
- 预订名额与接单状态在事务中检查，避免超售和重复接单。
- 取消订单保留记录；有历史订单关联的线路和用户不能直接删除，可下架或停用。
- 有生效预订时限制修改行程关键信息；不能将容量降低到已预订人数以下。

订单流转与技术说明见 [架构说明](docs/架构说明.md)，测试结果见 [验收记录](docs/验收记录.md)。

## 可选：连接 MySQL

默认 H2 演示模式已经可以完整使用。旧项目的 MySQL 实例、密码和数据没有迁移或修改。

若以后要切换，先在 MySQL 中创建**全新的独立数据库**，例如 `travel_agency_v2`，并给专用应用账号授予该库的权限。不要指向旧的 `travel_agency` 数据库，新旧表结构不兼容。

在运行终端自行设置以下环境变量，不要将密码写进代码或提交到版本库：

| 变量 | 用途 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=mysql` | 启用 MySQL 配置 |
| `TRAVEL_DB_URL` | JDBC URL，例如 `jdbc:mysql://127.0.0.1:3306/travel_agency_v2?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai` |
| `TRAVEL_DB_USER` | 新库的应用用户名 |
| `TRAVEL_DB_PASSWORD` | 数据库密码 |
| `TRAVEL_ADMIN_USERNAME` | 新库首次初始化的管理员账号 |
| `TRAVEL_ADMIN_PASSWORD` | 首次初始化的管理员密码，至少 12 位且含大小写字母、数字和符号 |

然后执行 `./scripts/dev.sh run`。Flyway 自动创建新版表结构；MySQL 模式不会生成演示用户和演示订单。首次管理员创建后应移除初始化密码环境变量。

MySQL 配置是可选迁移入口，本次验收针对 H2 本地模式；实际 MySQL 连接尚未验证。旧系统的导出文件只有建库语句，不能据此恢复旧业务记录，需要另行取得真实数据库导出后再制定迁移规则。

## 旧项目与备份

重构前目录完整归档在文稿目录的 `旅行社管理系统_重构前备份_20260909.tar.gz`，包括旧 Java/JSP、IDE 配置及独立 Tomcat。840 个文件的内容已逐一与原文件核对。

恢复时先停止应用，将该归档解压到另一个空目录核对；保留新版 `data/`，不要直接覆盖当前项目。备份含原有本地配置，应只在本机保存。
