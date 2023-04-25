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

    id("org.springframework.boot") version "2.7.5"
    id("org.springframework.cloud.contract") version "3.1.5"
    id("io.spring.dependency-management") version "1.1.0"

    // Auto Release
    id("net.researchgate.release") version "3.0.2"

    // Dependency Check
    id("org.owasp.dependencycheck") version "8.2.1"

    // Docker Container Build
    id("com.google.cloud.tools.jib") version "3.3.1"

    // Linter
    id("pmd")
    id("checkstyle")
    id("com.github.spotbugs") version "5.0.14"
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"

    // Kotlin
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.spring") version "1.8.21"
    kotlin("plugin.jpa") version "1.8.21"
    kotlin("kapt") version "1.8.21"

    // Dokka
    id("org.jetbrains.dokka") version "1.8.10"
    id("com.dorongold.task-tree") version "2.1.1"
}

group = "com.raynigon.raylevation"
java.sourceCompatibility = JavaVersion.VERSION_11

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
    implementation("com.raynigon.unit-api:spring-boot-jackson-starter:2.0.1")
    implementation("com.raynigon.unit-api:spring-boot-jpa-starter:2.0.1")
    implementation("com.raynigon.unit-api:unit-api-kotlin:2.0.1")

    // Logging
    implementation("com.raynigon.spring-boot:ecs-logging-app:1.1.4")
    implementation("com.raynigon.spring-boot:ecs-logging-access:1.1.4")
    implementation("com.raynigon.spring-boot:ecs-logging-async:1.1.4")

    // Helpers
    implementation("org.gdal:gdal:3.4.0")
    implementation("com.github.davidmoten:rtree:0.10")
    implementation("com.github.davidmoten:geo:0.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")

    // Spock
    testImplementation("org.codehaus.groovy:groovy-all:3.0.17")
    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
    testImplementation("org.spockframework:spock-spring:2.1-groovy-3.0")

    // Spring
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")

    // Testcontainers
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:spock")

    // Mockk
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("io.github.hakky54:logcaptor:2.9.0")

    // Documentation
    testImplementation("org.springdoc:springdoc-openapi-ui:1.7.0")
    testImplementation("com.raynigon.unit-api:spring-boot-springdoc-starter:2.0.1")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.18.0")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2021.0.5")
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
            "v1/*.yaml"
        )
    )

    ignoredFiles.set(emptyList())

    testFramework.set(org.springframework.cloud.contract.verifier.config.TestFramework.JUNIT5)
    val namespace = "com.raynigon.raylevation.contracts"

    baseClassMappings.baseClassMapping(
        ".*\\.v1\\.lookup",
        "$namespace.RaylevationControllerV1Base"
    )
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
            jvmTarget = "11"
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
        image = "ghcr.io/raynigon/raylevation-gdal-base:3.4.3"
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
        environment = mapOf(
            "SPRING_MAIN_BANNER-MODE" to "off"
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
            "The Raylevation Server Docker Image provides the application with all dependencies"
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
