plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "golf"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // 邮件提醒：dev 模式只写日志，prod 模式走 SMTP（JavaMailSender）
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // 邮件正文的 HTML 模板，放在 resources/templates/mail/
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 源码是 UTF-8，别让 javac 跟着平台编码走
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// bootRun 的子进程输出走管道回 Gradle，强制按 UTF-8 写，配合 gradle.properties 里的 daemon 编码
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}
