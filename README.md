# 山海行旅行社管理系统

山海行是一套基于 **Spring Boot 3.5 + React 19 + TypeScript** 的旅行社管理系统，提供管理员、旅行顾问和旅行者三种角色，覆盖线路管理、线路预订、订单协作、经营报表、优惠码与线路评价。

后端用 **JDBC + Flyway**（不引 ORM），前端不引 UI 框架与图表库，图表与星级都是手写 SVG。默认使用随程序启动的本地 H2 演示库，不需要安装 MySQL。

Windows 运行压缩包同时包含**可直接运行的程序**和**前后端源码**，普通演示不需要安装 Node.js、Maven 或 MySQL。GitHub 仓库保存源码、图片资源、文档和启动脚本，不包含编译产物与本机 `data` 数据。

## 功能清单

| 功能 | 说明 | 页面入口 | 文档 |
| --- | --- | --- | --- |
| 订单审计留痕 | 每次状态变更写一行事件（前状态、后状态、操作人、当时角色、时间），与状态变更同事务；时间线只追加 | 订单详情「订单变更记录」 | `docs/架构说明.md`「订单变更留痕」 |
| 分页与索引下推 | 可见性翻译成 SQL 条件，`page/size/sort` 服务端分页；带分页参数返回信封，不带仍返回数组 | 订单列表页脚与状态页签 | `docs/API.md`「分页契约」 |
| 线路封面上传 | 只认 PNG/JPEG/WebP 且以文件头魔数为准，≤5MB；存盘名 UUID，原始文件名不入库 | 线路管理 → 编辑 → 选择封面 | `docs/API.md`「线路」 |
| 报表聚合 | 状态、营收、月度序列、热门线路、员工排行全部 SQL 聚合；工作台与报表两套口径 | 「经营报表」（管理员/顾问可见） | `docs/架构说明.md`「报表与统计」 |
| 优惠码 | 折扣率配置、四道核销校验、条件自增防超发、订单存减免快照 | 下单弹窗「优惠码（选填）」；配置走 Swagger UI | `docs/API.md`「优惠码」 |
| 线路评价 | 仅已完成且属于本人的订单可评，一单一评，列表均分一次聚合（无 N+1） | 线路卡片星级；提交走 Swagger UI | `docs/API.md`「评价」 |

> 优惠码配置与评价提交目前只有接口、没有页面入口，演示与联调用 <http://127.0.0.1:8080/travel/swagger-ui/index.html>。

## 环境要求

| 用途 | 需要 |
| --- | --- |
| 只运行 jar | JDK 21（64 位） |
| 从源码构建后端 | JDK 21；Maven 3.9+ **或**直接用仓库自带的 `./mvnw`（首次运行自动下载 Maven 3.9.9） |
| 构建前端 | Node.js 22.12+、npm |

## 从 GitHub 获取源码并构建

```bash
git clone https://github.com/Rqing26/tour.git
cd tour

# 后端测试 + 打包（用仓库自带的 wrapper，不必本机装 Maven）
./mvnw -B -ntp verify          # Windows 命令行用 mvnw.cmd -B -ntp verify
```

前端产物要先构建进 `src/main/resources/static/` 才会被打进 jar：

```bash
cd frontend
npm install
npm test                       # Node 自带测试工具，纯 TS 断言
npm run build
cp -r dist/. ../src/main/resources/static/   # Windows: xcopy dist\* ..\src\main\resources\static\ /E /I /Y
cd ..
./mvnw -B -ntp package
java -jar target/travel-agency.jar
```

启动后访问 **http://127.0.0.1:8080/travel/**。

Windows 上也可以双击 `build-windows.bat`（该脚本调用本机 `mvn` 与 `npm ci`，需要先装好 Maven），再用 `start-windows.bat` 启动。

### 常用命令

| 目的 | 命令 |
| --- | --- |
| 后端全部测试（H2 内存库，不依赖外部服务） | `./mvnw -B -ntp verify` |
| 只跑一个测试类 | `./mvnw -B -ntp -Dtest=OrderPaginationTest test` |
| 前端测试 / 构建 / 格式与静态检查 | `cd frontend && npm test && npm run build && npm run format:check && npm run lint` |
| 换端口启动（冒烟脚本打 18080） | `java -jar target/travel-agency.jar --server.port=18080` |
| 用一次性内存库启动（不写 `data/`） | `java -jar target/travel-agency.jar "--spring.datasource.url=jdbc:h2:mem:demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" --spring.profiles.active=demo` |
| 连接 MySQL | `--spring.profiles.active=mysql`（配置见 `src/main/resources/application-mysql.yml`） |

## 一、先运行起来（压缩包形态）

1. 将整个压缩包解压到一个普通文件夹，例如 `D:\山海行`。不要在压缩软件的预览窗口里直接运行。
2. 安装 **Java 21（64 位）**，并在命令提示符中运行 `java -version` 确认。可从 [Eclipse Temurin 官方下载页](https://adoptium.net/temurin/releases/)选择 **21 / Windows / 对应电脑架构**的 JRE 或 JDK。已安装可用的 Java 21 时跳过此步。
3. 双击文件夹里的 `start-windows.bat`。黑色运行窗口要保持打开；看到 `Started TravelApplication` 后，打开浏览器访问 **http://127.0.0.1:8080/travel/**。
4. 首次运行会在同一文件夹下自动生成 `data`，并填入演示线路、订单和账号。以后重新启动仍会保留这台电脑上的改动。

本地演示账号：

| 身份 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `Travel@2026` |
| 旅行顾问 | `employee` | `Travel@2026` |
| 旅行者 | `customer` | `Travel@2026` |

首次启动会自动灌入 6 条线路、8 笔订单、4 个账号（另有 `demo_guest` 作为第二个客户，用来演示"看不到别人的订单"）。这是公开的演示账号，系统默认只监听运行它的电脑，演示数据不适合存放真实客户资料。页面中的金额是订单金额，系统没有在线支付功能。

### 账户设置

在右上角头像或侧栏「个人账户」中，可以修改姓名、联系电话和密码。资料修改支持撤销；密码更新成功后需要重新登录。

头像支持 JPG、PNG 和 WebP，单张不超过 5 MB。图片会居中裁切并缩小，按账户保存在当前浏览器；清除本站浏览器数据后会恢复默认头像，不会同步到其他设备。

## 二、停止与保存数据

在运行窗口按 `Ctrl+C`，或关闭该窗口，即可停止。需要备份本机演示操作时，先停止程序，再复制整个 `data` 文件夹；不要只复制其中某一个数据库文件。收到的新压缩包**不含发送方电脑上的 `data`**。

上传的线路封面存在 `data/uploads/`（可用 `travel.upload.dir` 改），同样随 `data` 一起备份。

## 三、目录与文档

```text
src/main/java/            后端：controller / service / repository / dto / config
src/main/resources/db/    Flyway 迁移 V1–V6（V1 不可改，只能新增）
frontend/src/             React 页面、组件与纯函数模块（*Query.ts / chart.ts 等可单测）
frontend/tests/           前端测试（node --test）
scripts/                  smoke_test.py 等验收脚本
docs/                     接口、架构、验收、演示与进度文档
```

| 文档 | 内容 |
| --- | --- |
| `docs/API.md` | 全部 HTTP 端点、错误码语义、分页契约与响应模型（C4 基线） |
| `docs/架构说明.md` | 请求链路、六张表与迁移、订单状态机、各功能的设计取舍 |
| `docs/演示脚本.md` | 六项功能的逐步演示、可核对的数据与常见提问 |
| `docs/验收记录.md` | 回归结果、测试清单、兼容性红线复核 |
| `docs/进度看板.md` | 任务状态与每日记录 |
| `docs/开发日志.md` | 按周记录的协作与踩坑 |

接口文档运行时也可看：`/travel/swagger-ui/index.html`、`/travel/v3/api-docs`；健康检查 `/travel/actuator/health`。

## 四、常见问题

- **双击后提示找不到 Java**：重新打开命令提示符运行 `java -version`；若仍找不到，检查 Java 安装及 PATH，也可以设置 `JAVA_HOME` 指向 Java 21 安装目录。
- **提示 Java 类文件版本不兼容**：正在使用的 Java 太旧，请安装 Java 21，并确认 `java -version` 显示的是新版本。
- **提示 8080 端口被占用**：先关闭另一份正在运行的山海行或其他占用 8080 的程序，或改用 `--server.port=18080`。
- **浏览器打不开**：确认黑色窗口仍在运行，地址是 `http://127.0.0.1:8080/travel/`，并查看窗口末尾的报错。不要直接双击 `index.html`。
- **构建时前端报 Prettier 不通过**：本机 Git 若把工作区检出成 CRLF，`format:check` 会全量报红。在仓库里执行 `git config core.autocrlf false` 后重新检出。
- **改了前端但页面没变**：前端产物要先 `npm run build` 并复制到 `src/main/resources/static/`，再重新打包 jar。
- **防火墙提示**：这是本机网页演示；默认只绑定 `127.0.0.1`，不需要允许公共网络访问。

本包已在 Windows 本机以 jar 形式验证过启动流程（干净内存库与已有 `data` 两种情形）；容器环境的构建与编排见 `Dockerfile` 与 `docker-compose.yml`。
