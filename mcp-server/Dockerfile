# 演示 MCP Server 镜像：多阶段构建，仅打包 mcp-server 可执行 jar
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY mcp-server mcp-server
COPY tenant tenant
COPY tools tools
COPY memory memory
COPY observe observe
COPY runtime runtime
COPY gateway-api gateway-api
COPY bootstrap bootstrap
RUN mvn -pl mcp-server -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /src/mcp-server/target/mcp-server-*.jar /app/app.jar
# 容器内需对 0.0.0.0 监听，宿主机才能用 localhost:19099 访问
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 19099
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
