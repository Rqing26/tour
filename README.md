# 山海行旅行社管理系统：Windows 使用说明

山海行是一套基于 **Spring Boot + React + TypeScript** 的旅行社管理系统，提供管理员、旅行顾问和旅行者三种角色，覆盖线路管理、线路预订、订单协作和用户管理。

Windows 运行压缩包同时包含**可直接运行的程序**和**前后端源码**，普通演示不需要安装 Node.js、Maven 或 MySQL，也不用重新编译。GitHub 仓库保存源码、图片资源、文档和启动脚本，不包含编译产物与本机 `data` 数据。

## 从 GitHub 获取源码

安装 **JDK 21、Maven 3.9+、Node.js 22.12+** 后执行：

```powershell
git clone https://github.com/Rqing26/tour.git
cd tour
.\build-windows.bat
.\start-windows.bat
```

构建脚本下载依赖、构建前端并运行后端测试，生成 `target/travel-agency.jar`。启动后访问 **http://127.0.0.1:8080/travel/**。默认使用本地 H2 数据库，无需安装 MySQL。

## 一、先运行起来

1. 将整个压缩包解压到一个普通文件夹，例如 `D:\山海行`。不要在压缩软件的预览窗口里直接运行。
2. 安装 **Java 21（64 位）**，并在 Windows 的命令提示符中运行 `java -version` 确认。可从 [Eclipse Temurin 官方下载页](https://adoptium.net/temurin/releases/)选择 **21 / Windows / 对应电脑架构**的 JRE 或 JDK。已安装可用的 Java 21 时跳过此步。
3. 双击文件夹里的 `start-windows.bat`。黑色运行窗口要保持打开；看到 `Started TravelApplication` 后，打开浏览器访问 **http://127.0.0.1:8080/travel/**。
4. 首次运行会在同一文件夹下自动生成 `data`，并填入演示线路、订单和账号。以后重新启动仍会保留这台电脑上的改动。

本地演示账号：

| 身份 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `Travel@2026` |
| 旅行顾问 | `employee` | `Travel@2026` |
| 旅行者 | `customer` | `Travel@2026` |

这是公开的演示账号。系统默认只监听运行它的电脑，演示数据不适合存放真实客户资料。页面中的金额是订单金额，系统没有在线支付功能。

## 二、停止与保存数据

在运行窗口按 `Ctrl+C`，或关闭该窗口，即可停止。需要备份本机演示操作时，先停止程序，再复制整个 `data` 文件夹；不要只复制其中某一个数据库文件。收到的新压缩包**不含发送方电脑上的 `data`**。

## 三、源码与重新构建

`src/` 是 Spring Boot 后端，`frontend/` 是 React + TypeScript 前端；`target/travel-agency.jar` 是构建生成的程序。Windows 运行压缩包已包含该文件，直接演示不用重新构建；从 GitHub 获取源码后需要先构建。

修改源码后，需另装 **JDK 21、Maven 3.9+、Node.js 22.12+**，连接网络下载依赖，再双击 `build-windows.bat`。脚本会构建前端、运行后端测试，并更新 `target/travel-agency.jar`。构建前请先停掉正在运行的程序，构建后再运行 `start-windows.bat`。

包内的 `docs/原项目README.md` 和其他 `docs/` 文件记录项目设计与业务规则；其中的 macOS/Linux 启动命令不适用于 Windows，请以本文件为准。默认使用随程序启动的本地 H2 演示数据库，不需要连接原项目 MySQL。

## 四、常见问题

- **双击后提示找不到 Java**：重新打开命令提示符运行 `java -version`；若仍找不到，检查 Java 安装及 PATH。也可以设置 `JAVA_HOME` 指向 Java 21 安装目录。
- **提示 Java 类文件版本不兼容**：正在使用的 Java 太旧，请安装 Java 21，并确认 `java -version` 显示的是新版本。
- **提示 8080 端口被占用**：先关闭另一份正在运行的山海行或其他占用 8080 的程序，再重新启动。
- **浏览器打不开**：确认黑色窗口仍在运行，地址是 `http://127.0.0.1:8080/travel/`，并查看窗口末尾的报错。不要直接双击 `index.html`。
- **防火墙提示**：这是本机网页演示；默认只绑定 `127.0.0.1`，不需要允许公共网络访问。

本包已经在 macOS 上用全新空数据目录验证了启动流程；Windows 实机运行仍需收件人按上述步骤确认。
