# --- 1단계: 빌드 (Build Stage) ---
# Java 21을 지원하는 Gradle 8.5+ 이미지 사용
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# 캐시 효율을 위해 설정 파일 먼저 복사
COPY build.gradle.kts settings.gradle.kts ./

# 소스 코드 복사
COPY src ./src

# 빌드 수행 (테스트 제외, 데몬 미사용)
# Spring Boot 3.x는 *-plain.jar가 같이 생성되므로 bootJar만 남기기 위해 jar 태스크를 제외할 수도 있지만,
# 아래 COPY 단계에서 처리하도록 하겠습니다.
RUN gradle clean build -x test --no-daemon

# --- 2단계: 실행 (Run Stage) ---
# Java 21 런타임 이미지 (가볍고 호환성 좋은 eclipse-temurin 또는 openjdk 사용)
FROM openjdk:21-jdk-slim

WORKDIR /app

# 빌드 결과물 복사
# 주의: Spring Boot 3.x는 빌드 시 '-plain.jar' 파일도 생성합니다.
# 겹치지 않게 실행 가능한 jar(용량이 더 큰 것) 하나만 복사하도록 설정합니다.
# 만약 복사 에러가 나면 build.gradle.kts 설정을 수정해야 합니다 (아래 팁 참조).
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

# Spring Boot 기본 포트
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]