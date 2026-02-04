plugins {
	kotlin("jvm") version "1.9.25" // 혹은 사용중인 최신 버전 (2.x는 아직 베타일 수 있음)
	kotlin("plugin.spring") version "1.9.25"
	kotlin("plugin.jpa") version "1.9.25"
	// 중요: Spring Boot 4.0.1은 아직 없습니다. 최신 안정 버전인 3.4.1로 변경합니다.
	id("org.springframework.boot") version "3.4.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ats"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		// Java 25가 설치되어 있다면 유지, 없으면 21로 변경
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// [중요] webmvc -> web으로 변경 (Tomcat 포함된 완전한 웹 스타터)
	implementation("org.springframework.boot:spring-boot-starter-web")

	// JPA
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")

	// Kotlin 필수 모듈
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// [중요] Jackson 패키지명 수정 (tools.jackson -> com.fasterxml.jackson)
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	// Swagger
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

	// Validation
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// CSV
	implementation("com.opencsv:opencsv:5.9")

	// MySQL
	runtimeOnly("com.mysql:mysql-connector-j")

	//Email
	implementation("org.springframework.boot:spring-boot-starter-mail")

	implementation("org.apache.httpcomponents.client5:httpclient5")
	// [필수] 코루틴 핵심 라이브러리 (launch, runBlocking, Dispatchers 등)
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

	// [선택/권장] Spring과 코루틴을 매끄럽게 연결해주는 리액터 확장 (Spring Boot 사용 시 추천)
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
		// [수정] 명시적으로 JVM 타겟을 21로 지정하여 Java 설정과 맞춤
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
	}
}

// JPA Entity No-arg 생성자 자동 생성 설정
allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// build.gradle.kts 맨 아래에 추가
// 실행 불가능한 일반 jar(plain) 생성 끄기 -> Docker 빌드 시 파일 충돌 방지
tasks.getByName<Jar>("jar") {
	enabled = false
}