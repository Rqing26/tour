"""校验容器化配置的结构一致性（不依赖 docker，可在任意环境与 CI 中运行）。

检查的是「配置写错就会炸」的硬性事实，例如容器内绑定 127.0.0.1 会让端口映射失效、
镜像构建里跳过测试、前端产物没进 static 导致页面 404。真正的启动验证由 CI 的
container job 用 docker 完成。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FAILURES = []
PASSES = []


def check(label, ok, detail=""):
    if ok:
        PASSES.append(label)
        print(f"PASS {label}", flush=True)
    else:
        FAILURES.append((label, detail))
        print(f"FAIL {label} {detail}".rstrip(), flush=True)


def read(name):
    path = ROOT / name
    if not path.is_file():
        check(f"{name} 存在", False, "文件缺失")
        return ""
    return path.read_text(encoding="utf-8")


def service_blocks(compose_text):
    """按缩进切出 services 下每个服务的文本块，避免为一个 compose 文件引入 YAML 依赖。"""
    blocks = {}
    lines = compose_text.splitlines()
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == "services:")
    except StopIteration:
        return blocks
    current = None
    for line in lines[start + 1 :]:
        if not line.strip() or line.lstrip().startswith("#"):
            if current:
                blocks[current] += line + "\n"
            continue
        indent = len(line) - len(line.lstrip())
        if indent <= 1:
            break
        if indent == 2 and re.fullmatch(r"\s{2}[\w.-]+:\s*", line):
            current = line.strip().rstrip(":")
            blocks[current] = ""
            continue
        if current:
            blocks[current] += line + "\n"
    return blocks


def main():
    compose = read("docker-compose.yml")
    dockerfile = read("Dockerfile")
    dockerignore = read(".dockerignore")
    if not compose or not dockerfile or not dockerignore:
        return report()

    blocks = service_blocks(compose)
    check(
        "compose 解析出 app / app-mysql / db 三个服务",
        set(blocks) == {"app", "app-mysql", "db"},
        f"实际 {sorted(blocks)}",
    )
    app = blocks.get("app", "")
    app_mysql = blocks.get("app-mysql", "")
    db = blocks.get("db", "")

    # ---- 默认环境必须能直接用 H2 起起来 ----
    check("app 不带 profile，docker compose up 默认只起 H2 环境", "profiles:" not in app)
    check(
        "app-mysql 与 db 归入 mysql profile，不与默认环境抢端口",
        'profiles: ["mysql"]' in app_mysql and 'profiles: ["mysql"]' in db,
    )
    check("app 使用 demo profile", re.search(r"SPRING_PROFILES_ACTIVE:\s*demo", app) is not None)
    check(
        "app 绑定 0.0.0.0（application.yml 默认 127.0.0.1，容器内会让端口映射失效）",
        re.search(r"SERVER_ADDRESS:\s*0\.0\.0\.0", app) is not None,
    )
    check("app 映射 8080:8080", '"8080:8080"' in app)
    check(
        "app 把 H2 文件库挂到命名卷，重建容器不丢数据",
        "travel-data:/app/data" in app and re.search(r"^\s{2}travel-data:", compose, re.M) is not None,
    )
    check(
        "app 健康检查指向 /travel 下的公开接口",
        "healthcheck:" in app and "/travel/api/auth/config" in app,
    )

    # ---- MySQL 环境必须等数据库就绪再启动 ----
    for name in ("TRAVEL_DB_URL", "TRAVEL_DB_USER", "TRAVEL_DB_PASSWORD"):
        check(f"app-mysql 注入 {name}", name in app_mysql)
    check(
        "app-mysql 等到 db 健康后再启动",
        "depends_on:" in app_mysql and "condition: service_healthy" in app_mysql,
    )
    check("app-mysql 映射 8081，避免和 H2 环境冲突", '"8081:8080"' in app_mysql)
    check(
        "db 使用 MySQL 8 镜像",
        re.search(r"image:\s*mysql:8(\.\d+)?\s*$", db, re.M) is not None,
    )
    check("db 有 mysqladmin 健康检查", "mysqladmin ping" in db)
    check("db 数据落到命名卷 travel-mysql", "travel-mysql:/var/lib/mysql" in db)
    check(
        "compose 不挂载宿主机源码或本机 data 目录",
        not re.search(r"-\s*\./?\S*:/app\b(?!\S*dist)", app + app_mysql),
    )

    # ---- 镜像构建必须可复现、不跳过测试 ----
    stages = re.findall(r"^FROM\s+\S+\s+AS\s+(\w+)", dockerfile, re.M)
    check("Dockerfile 为多阶段：web / backend / runtime", stages == ["web", "backend", "runtime"], f"实际 {stages}")
    check(
        "构建阶段不跳过测试",
        "-DskipTests" not in dockerfile and "-Dmaven.test.skip" not in dockerfile,
    )
    check(
        "前端产物复制进 src/main/resources/static（否则 jar 里没有页面）",
        re.search(r"COPY\s+--from=web\s+\S*dist\s+\./src/main/resources/static", dockerfile) is not None,
    )
    check("运行时非 root 用户", re.search(r"^USER\s+(?!root)\w+", dockerfile, re.M) is not None)
    check("EXPOSE 8080", re.search(r"^EXPOSE\s+8080", dockerfile, re.M) is not None)
    check("ENTRYPOINT 用 java -jar 启动", "java $JAVA_OPTS -jar /app/app.jar" in dockerfile)
    check(
        "镜像内置 SERVER_ADDRESS=0.0.0.0",
        re.search(r"SERVER_ADDRESS=0\.0\.0\.0", dockerfile) is not None,
    )

    # ---- 构建上下文要瘦身，且不能夹带本机数据 ----
    for pattern in ("target/", "data/", "node_modules/", "frontend/dist/", ".runtime/"):
        check(f".dockerignore 排除 {pattern}", pattern in dockerignore)

    return report()


def report():
    print(f"\n{len(PASSES)} passed, {len(FAILURES)} failed", flush=True)
    for label, detail in FAILURES:
        print(f"  - {label} {detail}".rstrip(), flush=True)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
