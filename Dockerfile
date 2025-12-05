# Arm 빌드 최적화를 위해 빌드 스테이지 외부화
FROM gcr.io/distroless/java21-debian12

WORKDIR /app

COPY build/extracted/dependencies/ ./
COPY build/extracted/spring-boot-loader/ ./
COPY build/extracted/snapshot-dependencies/ ./
COPY build/extracted/application/ ./

USER nonroot
EXPOSE 8080

ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]