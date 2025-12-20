plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("kapt") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.yaklede"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

	kapt("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk:1.13.12")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.register<JavaExec>("runReport") {
	group = "research"
	description = "Run backtest diagnostics report"
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("io.github.yaklede.elliott.wave.principle.coin.ApplicationKt")
	args("--bot.mode=BACKTEST", "--research.enabled=true", "--research.mode=REPORT")
}

tasks.register<JavaExec>("runAblation") {
	group = "research"
	description = "Run ablation comparisons"
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("io.github.yaklede.elliott.wave.principle.coin.ApplicationKt")
	args("--bot.mode=BACKTEST", "--research.enabled=true", "--research.mode=ABLATION")
}

tasks.register<JavaExec>("runWalkForward") {
	group = "research"
	description = "Run walk-forward validation"
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("io.github.yaklede.elliott.wave.principle.coin.ApplicationKt")
	args("--bot.mode=BACKTEST", "--research.enabled=true", "--research.mode=WALK_FORWARD")
}
