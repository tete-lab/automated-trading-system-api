# --- 1단계: 빌드 (Build Stage) ---
# Gradle 공식 이미지는 계속 지원되므로 유지합니다.
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# 설정 파일 복사
COPY build.gradle.kts settings.gradle.kts ./

# 소스 코드 복사
COPY src ./src

# 빌드 수행 (테스트 제외)
RUN gradle clean build -x test --no-daemon

# --- 2단계: 실행 (Run Stage) ---
# [수정] openjdk -> eclipse-temurin (Java 21의 표준 이미지)
# -jre 태그를 사용하여 실행에 필요한 런타임만 포함 (용량 절약)
FROM eclipse-temurin:21-jre

WORKDIR /app

# 빌드 결과물 복사
# (참고: build.gradle.kts에 jar { enabled = false } 설정을 추가했다면 *-SNAPSHOT.jar만 복사됩니다)
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

# 포트 설정
EXPOSE 8080

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]