#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  elif [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    export JAVA_HOME
  fi
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少 $1，请先阅读 README.md 的环境说明。" >&2; exit 1; }
}
build_frontend() {
  require_command npm
  (cd frontend && npm ci --no-audit --no-fund && npm run build)
  # 此目录只保存构建生成的前端资源；源代码位于 frontend/src。
  mkdir -p src/main/resources/static
  find src/main/resources/static -mindepth 1 -maxdepth 1 ! -name '.gitkeep' -exec rm -rf {} +
  cp -R frontend/dist/. src/main/resources/static/
}
build_all() {
  require_command mvn
  require_command java
  build_frontend
  mvn -B -ntp clean verify
}
PID_FILE="$PROJECT_DIR/data/server.pid"
JAR_FILE="$PROJECT_DIR/target/travel-agency.jar"
server_is_running() {
  [ -f "$PID_FILE" ] || return 1
  SERVER_PID="$(cat "$PID_FILE")"
  case "$SERVER_PID" in ''|*[!0-9]*) return 1 ;; esac
  SERVER_COMMAND="$(ps -p "$SERVER_PID" -o command= 2>/dev/null || true)"
  case "$SERVER_COMMAND" in *"$JAR_FILE"*) return 0 ;; *) return 1 ;; esac
}
case "${1:-help}" in
  build)
    build_all
    ;;
  run|debug)
    require_command java
    if server_is_running; then
      echo "本项目已在运行：http://127.0.0.1:8080/travel/"
      echo "需要重启时，先运行 ./scripts/dev.sh stop。"
      exit 0
    fi
    if [ ! -f target/travel-agency.jar ]; then build_all; fi
    mkdir -p data
    echo "$$" > "$PID_FILE"
    if [ "${1}" = debug ]; then
      exec java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005 -jar "$JAR_FILE"
    else
      exec java -jar "$JAR_FILE"
    fi
    ;;
  stop)
    if server_is_running; then
      kill -TERM "$SERVER_PID"
      echo "已向本项目发送停止指令。"
    else
      echo "本项目当前没有由启动脚本运行的服务。"
    fi
    rm -f "$PID_FILE"
    ;;
  test)
    require_command mvn
    (cd frontend && npm ci --no-audit --no-fund && npm run build)
    mvn -B -ntp test
    ;;
  frontend)
    require_command npm
    cd frontend
    if [ ! -d node_modules ]; then npm ci --no-audit --no-fund; fi
    exec npm run dev -- --host 127.0.0.1
    ;;
  *)
    cat <<'HELP'
山海行 · 本地开发
  ./scripts/dev.sh build     构建前端、运行后端测试并打包
  ./scripts/dev.sh run       启动已构建的应用（首次自动构建）
  ./scripts/dev.sh stop      停止本项目的本地服务
  ./scripts/dev.sh debug     启动 Java 调试，端口 5005
  ./scripts/dev.sh frontend  前端开发服务器，保存后即时更新
  ./scripts/dev.sh test      前端类型与构建检查、后端测试

完整应用：http://127.0.0.1:8080/travel/
前端开发：http://127.0.0.1:5173/travel/（需同时启动后端）
修改代码后先重新 build 再 run；在运行终端按 Ctrl+C 停止。
HELP
    ;;
esac
