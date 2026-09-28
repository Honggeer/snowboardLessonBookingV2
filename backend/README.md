# 后端

Java 25 / Spring Boot 4.1.1 模块化单体。`scheduling` 中的时间范围预览是本地架构示例，不提供约课能力。MySQL 由 Flyway 管理；V1 只建立迁移历史，不含业务表。

## 运行与测试

本地整体启动见 [部署说明](../deploy/README.md)。独立开发后端时需要 Java 25 与 MySQL 8.4，设置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，然后运行 `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`。`local` profile 才开放演示预览 API。

`./mvnw test` 包含真实 MySQL 8.4 的 Testcontainers 测试，需要可用的 Docker 守护进程。macOS Colima 示例：

```sh
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw test
```

架构测试覆盖已有实际类与禁止依赖的反例。Web 测试覆盖未登录、CSRF、获取 token 后创建、有效输入和错误输入。健康端点是 `/actuator/health`；其他请求默认要求认证。当前没有生产身份模块。`local` 演示客户端需先以本地 Basic 认证请求 `/api/demo/time-window-previews/csrf`，保留会话 cookie，然后把返回的 token 放入其指定的请求头，再调用创建接口。
