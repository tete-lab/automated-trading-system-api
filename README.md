# 📈 ATS Server (Stock Automated Trading System)

주식 자동매매 시스템(Automated Trading System)을 위한 백엔드 API 서버입니다.
**Kotlin**과 **Spring Boot**를 기반으로 하며, 확장성과 유지보수를 위해 **도메인형 패키지 구조(Package by Feature)**를 채택했습니다.

## 🛠 Tech Stack

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-purple?style=flat&logo=kotlin)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green?style=flat&logo=springboot)
![Java](https://img.shields.io/badge/Java-21%2B-red?style=flat&logo=openjdk)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat&logo=mysql)
![JPA](https://img.shields.io/badge/JPA-Hibernate-lightgrey?style=flat)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI-85EA2D?style=flat&logo=swagger)

## 📂 Project Structure

기능별 응집도를 높이기 위해 도메인 단위로 패키지를 분리했습니다.

```text
com.ats.server
 ├── domain               # 비즈니스 도메인 (기능별 분리)
 │    ├── sysconfig       # 시스템 전역 설정
 │    ├── member          # 회원 관리 (가입, 로그인)
 │    └── account         # 주식 계좌 및 API 키 관리
 └── global               # 전역 공통 설정
      ├── config          # Swagger, Security 등 설정
      └── entity          # BaseEntity (Auditing) 등
```

# 🚀 Key Features
- 시스템 설정 (SysConfig): 서버 전역 변수 동적 관리
- 회원 (Member): 사용자 관리 및 인증 기반 마련
- 계좌 (Account): 증권사 API 연동을 위한 계좌 및 Key 관리
- API 문서화: Swagger UI를 통한 실시간 API 테스트 지원

# ⚙️ Getting Started
### 1. Prerequisites
- Java JDK 21 이상 (권장 25)
- MySQL / MariaDB

### 2. Configuration
- 보안을 위해 application.yml은 Git에 포함되지 않습니다. src/main/resources 경로에 파일을 생성하고 아래 내용을 입력하세요.


``` properties
spring:
datasource:
url: jdbc:mysql://localhost:3306/ats_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
username: ats_user
password: YOUR_PASSWORD
driver-class-name: com.mysql.cj.jdbc.Driver
jpa:
hibernate:
ddl-auto: update
show-sql: true
```

### 3. Run
```text
# Mac / Linux
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

# 📚 API Documentation
서버 실행 후 아래 주소에서 API 명세를 확인할 수 있습니다.

- Swagger UI: http://localhost:8080/swagger-ui/index.html


---
Developed by Tetelab (updated 2026.01.13 )

