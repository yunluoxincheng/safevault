FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY src src
RUN mvn -B -Dmaven.test.skip=true clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /workspace/target/safevault-backend-*.jar /app/app.jar

# Run with non-root user in production containers.
RUN useradd -r -u 10001 -g root appuser \
    && chown appuser:root /app/app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
