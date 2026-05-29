plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

group = "ai.antop"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

// extra["jackson-bom.version"] = libs.versions.jackson.get()

dependencies {
    implementation(libs.spring.boot.starter.security)
    implementation(libs.easy.captcha)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.moudle.kotlin)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    implementation(libs.p6spy)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.kotlin.logging)
    implementation(libs.pdfbox)
    implementation(libs.jai.imageio.jpeg2000)
    implementation(libs.openai.java.spring.boot.starter)
    implementation(libs.sitemap.starter)
    implementation(libs.rome)
    runtimeOnly(libs.sqlite.jdbc)
    runtimeOnly(libs.hibernate.community.dialects)
    developmentOnly(libs.spring.boot.devtools)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.mariadb.java.client)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
