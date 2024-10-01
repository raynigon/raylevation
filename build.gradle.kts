import org.jetbrains.kotlin.gradle.tasks.Kapt
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import java.time.OffsetDateTime

plugins {
    jacoco
    id("idea")
    id("groovy")
    id("jacoco")
    id("signing")
    id("maven-publish")

    id("org.springframework.boot") version "3.2.5"
    id("org.springframework.cloud.contract") version "4.1.4"
    id("io.spring.dependency-management") version "1.1.6"

    // Auto Release
    id("net.researchgate.release") version "3.0.2"

    // Dependency Check
    id("org.owasp.dependencycheck") version "10.0.3"

    // Docker Container Build
    id("com.google.cloud.tools.jib") version "3.4.3"

    // Linter
    id("pmd")
    id("checkstyle")
    id("com.github.spotbugs") version "6.0.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"

    // Kotlin
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "1.9.23"
    kotlin("plugin.jpa") version "2.0.0"
    kotlin("kapt") version "2.0.0"

    // Dokka
    id("org.jetbrains.dokka") version "1.9.20"
    id("com.dorongold.task-tree") version "4.0.0"
}

group = "com.raynigon.raylevation"
java.sourceCompatibility = JavaVersion.VERSION_17

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    maven {
        url = URI.create("https://repo.osgeo.org/repository/release/")
    }
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // resilience4j
    implementation("io.github.resilience4j:resilience4j-all")
    implementation("io.github.resilience4j:resilience4j-kotlin")
    implementation("io.github.resilience4j:resilience4j-spring-boot2")

    // unit-api
    implementation("com.raynigon.unit-api:spring-boot-jackson-starter:3.0.8")
    implementation("com.raynigon.unit-api:spring-boot-jpa-starter:3.0.8")
    implementation("com.raynigon.unit-api:unit-api-kotlin:3.0.8")

    // Logging
    implementation("com.raynigon.spring-boot:ecs-logging-app:2.1.7")
    implementation("com.raynigon.spring-boot:ecs-logging-access:2.1.6")
    implementation("com.raynigon.spring-boot:ecs-logging-async:2.1.6")

    // Helpers
    implementation("org.gdal:gdal:3.8.0")
    implementation("com.github.davidmoten:rtree:0.11")
    implementation("com.github.davidmoten:geo:0.8.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")

    // Spock
    testImplementation("org.apache.groovy:groovy:4.0.22")
    testImplementation(platform("org.spockframework:spock-bom:2.4-M4-groovy-4.0"))
    testImplementation("org.spockframework:spock-core")
    testImplementation("org.spockframework:spock-spring")

    // Spring
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")

    // Testcontainers
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:spock")

    // Mockk
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("io.github.hakky54:logcaptor:2.9.2")

    // Documentation
    testImplementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    testImplementation("com.raynigon.unit-api:spring-boot-springdoc-starter:3.0.8")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.19.8")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.2")
    }
}

allOpen {
    annotation("org.springframework.stereotype.Service")
    annotation("javax.persistence.Entity")
}

contracts {
    failOnNoContracts.set(false)

    includedFiles.set(
        listOf(
            "v1/*.yaml",
        ),
    )

    ignoredFiles.set(emptyList())

    testFramework.set(org.springframework.cloud.contract.verifier.config.TestFramework.JUNIT5)
    val namespace = "com.raynigon.raylevation.contracts"

    baseClassMappings.baseClassMapping(
        ".*\\.v1\\.lookup",
        "$namespace.RaylevationControllerV1Base",
    )
}

tasks.withType<Kapt> {
    if (this.name == "kaptContractTestKotlin") {
        this.enabled = false
    }
}

tasks.withType<KaptGenerateStubs> {
    if (this.name == "kaptGenerateStubsContractTestKotlin") {
        this.enabled = false
    }
}

tasks {
    named("processContractTestResources") {
        dependsOn("generateContractTests")
    }
    withType<Test> {
        useJUnitPlatform()

        testClassesDirs += sourceSets.contractTest.get().output.classesDirs
        classpath += sourceSets.contractTest.get().runtimeClasspath

        finalizedBy(withType<JacocoReport>()) // report is always generated after tests run
    }
    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }
    withType<JacocoReport> {
        dependsOn("test")

        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(true)
        }
    }
}

jib {
    val commitHash: String = System.getenv("COMMIT_HASH") ?: "unknown_commit"
    from {
        image = "ghcr.io/raynigon/raylevation-gdal-base:3.8.5"
        auth {
            username = System.getenv("REGISTRY_USER")
            password = System.getenv("REGISTRY_PASSWORD")
        }
    }
    to {
        image = System.getenv("IMAGE_NAME") ?: "raynigon/raylevation"
        tags = setOf(commitHash, "v${project.version}")
        auth {
            username = System.getenv("REGISTRY_USER")
            password = System.getenv("REGISTRY_PASSWORD")
        }
    }
    container {
        mainClass = "com.raynigon.raylevation.RaylevationApplicationKt"
        ports = listOf("8080")
        user = "1000"
        environment =
            mapOf(
                "SPRING_MAIN_BANNER-MODE" to "off",
            )
        labels.put("org.opencontainers.image.created", OffsetDateTime.now().toString())
        labels.put("org.opencontainers.image.authors", "Raynigon <opensource@raynigon.de>")
        labels.put("org.opencontainers.image.url", "https://github.com/raynigon/raylevation/releases/tag/v${project.version}")
        labels.put("org.opencontainers.image.documentation", "https://raylevation.raynigon.com/")
        labels.put("org.opencontainers.image.source", "https://github.com/raynigon/raylevation")
        labels.put("org.opencontainers.image.version", project.version.toString())
        labels.put("org.opencontainers.image.revision", commitHash)
        labels.put("org.opencontainers.image.licenses", "Apache-2.0")
        labels.put("org.opencontainers.image.title", "Raylevation Server")
        labels.put(
            "org.opencontainers.image.description",
            "The Raylevation Server Docker Image provides the application with all dependencies",
        )
    }
}

apply(from = "$rootDir/gradle/scripts/versionClass.gradle")
apply(from = "$rootDir/gradle/scripts/javadoc.gradle")
apply(from = "$rootDir/gradle/scripts/test.gradle")
apply(from = "$rootDir/gradle/scripts/external.gradle")
apply(from = "$rootDir/gradle/scripts/release.gradle")
apply(from = "$rootDir/gradle/scripts/publishing.gradle")
apply(from = "$rootDir/gradle/scripts/setup.gradle")
