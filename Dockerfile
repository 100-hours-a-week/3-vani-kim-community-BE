# 1. Builder Stage: JAR 파일 생성
FROM gradle:8.5-jdk21-alpine AS builder

WORKDIR /build

COPY gradlew ./
COPY gradle/ ./gradle/
COPY build.gradle settings.gradle ./

# 라이브러리 캐시 활성화
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY . .

# 빌드 캐시 활성화
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew build --no-daemon -x test --build-cache

# --------------------------------------------------------
# 2. Extractor Stage: JAR 파일 계층 분리
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /build
# 빌드된 JAR 가져오기
COPY --from=builder /build/build/libs/*.jar app.jar
# 레이어 추출
RUN java -Djarmode=layertools -jar app.jar extract

# --------------------------------------------------------
# 3. Runtime Stage: 최종 실행 이미지
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 한국 시간으로 설정
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

ARG UID=10001
RUN adduser -D -H -u ${UID} -s /sbin/nologin appuser

COPY --from=extractor /build/dependencies/ ./
COPY --from=extractor /build/spring-boot-loader/ ./
COPY --from=extractor /build/snapshot-dependencies/ ./
COPY --from=extractor /build/application/ ./

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]