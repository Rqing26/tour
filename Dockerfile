# 山海行 · 多阶段镜像
# 阶段 1 构建前端静态资源；阶段 2 打包 Spring Boot jar（默认跑测试，不跳过）；
# 阶段 3 只保留 JDK 21 运行时，镜像里没有 Maven、Node 与源码。
# 用法：docker build -t shanhai-travel:local .

# ---------- 阶段 1：前端 ----------
FROM node:22-alpine AS web
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---------- 阶段 2：后端 ----------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
# 先单独解析依赖，源码改动时不必重新下载整棵依赖树
COPY pom.xml ./
RUN mvn -B -ntp dependency:resolve
COPY src ./src
# 前端产物必须落在 static 下才会被打进 jar（C8：浏览器访问路径仍是 /travel/）
COPY --from=web /build/frontend/dist ./src/main/resources/static
RUN mvn -B -ntp package

# ---------- 阶段 3：运行时 ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
LABEL org.opencontainers.image.title="shanhai-travel" \
      org.opencontainers.image.description="山海行旅行社管理系统" \
      org.opencontainers.image.source="https://github.com/Rqing26/tour"

# 用非 root 账号运行，H2 文件库写在 /app/data（由数据卷承载）
RUN addgroup -S travel && adduser -S -G travel -h /app travel
WORKDIR /app
COPY --from=backend --chown=travel:travel /build/target/travel-agency.jar /app/app.jar
RUN mkdir -p /app/data && chown -R travel:travel /app/data
USER travel

# application.yml 默认绑定 127.0.0.1，容器内必须改成 0.0.0.0，否则端口映射不通
ENV SPRING_PROFILES_ACTIVE=demo \
    SERVER_ADDRESS=0.0.0.0 \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai"

EXPOSE 8080
VOLUME ["/app/data"]

# 探活打公开的演示配置接口，不依赖登录态；T1.6 接入 Actuator 后可换成 /actuator/health
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/travel/api/auth/config" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
