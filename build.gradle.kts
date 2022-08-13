import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    jacoco
    id("idea")
    id("groovy")
    id("jacoco")
    id("signing")
    id("maven-publish")

    id("org.springframework.boot") version "2.7.2"
    id("org.springframework.cloud.contract") version "3.1.3"
    id("io.spring.dependency-management") version "1.0.13.RELEASE"

    // Auto Release
    id("net.researchgate.release") version "2.8.1"

    // Dependency Check
    id("org.owasp.dependencycheck") version "7.1.1"

    // Docker Container Build
    id("com.google.cloud.tools.jib") version "3.2.1"

    // Linter
    id("pmd")
    id("checkstyle")
    id("com.github.spotbugs") version "5.0.9"
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"

    // Kotlin
    kotlin("jvm") version "1.7.0"
    kotlin("plugin.spring") version "1.7.0"
    kotlin("plugin.jpa") version "1.7.10"
    kotlin("kapt") version "1.7.0"
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
    implementation(kotlin("allopen"))
    implementation("io.github.resilience4j:resilience4j-all")
    implementation("io.github.resilience4j:resilience4j-kotlin")
    implementation("io.github.resilience4j:resilience4j-spring-boot2")

    // unit-api
    implementation("com.raynigon.unit-api:spring-boot-jackson-starter:2.0.1")
    implementation("com.raynigon.unit-api:spring-boot-jpa-starter:2.0.1")
    implementation("com.raynigon.unit-api:unit-api-kotlin:2.0.1")

    // Logging
    implementation("com.raynigon.spring-boot:ecs-logging-app:1.1.2")
    implementation("com.raynigon.spring-boot:ecs-logging-access:1.1.2")
    implementation("com.raynigon.spring-boot:ecs-logging-async:1.1.2")

    // Helpers
    implementation("org.gdal:gdal:3.4.0")
    implementation("com.github.davidmoten:rtree:0.10")
    implementation("com.github.davidmoten:geo:0.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.1.3")

    // Spock
    testImplementation("org.codehaus.groovy:groovy-all:3.0.12")
    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
    testImplementation("org.spockframework:spock-spring:2.1-groovy-3.0")

    // Spring
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")

    // Testcontainers
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:spock")

    // Mockk
    testImplementation("io.mockk:mockk:1.12.5")
    testImplementation("io.github.hakky54:logcaptor:2.7.10")

    // Documentation
    testImplementation("org.springdoc:springdoc-openapi-ui:1.6.9")
    testImplementation("com.raynigon.unit-api:spring-boot-springdoc-starter:2.0.1")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.17.2")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2021.0.3")
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
        tags = setOf(commitHash)
        auth {
            username = System.getenv("REGISTRY_USER")
            password = System.getenv("REGISTRY_PASSWORD")
        }
    }
    container {
        mainClass = "com.raynigon.raylevation.RaylevationApplicationKt"
        ports = listOf("8080")
        environment = mapOf(
            "SPRING_MAIN_BANNER-MODE" to "off"
        )
    }
}

apply(from = "$rootDir/gradle/scripts/versionClass.gradle")
apply(from = "$rootDir/gradle/scripts/javadoc.gradle")
apply(from = "$rootDir/gradle/scripts/test.gradle")
apply(from = "$rootDir/gradle/scripts/updateMkdocsConfig.gradle")
apply(from = "$rootDir/gradle/scripts/release.gradle")
apply(from = "$rootDir/gradle/scripts/publishing.gradle")
apply(from = "$rootDir/gradle/scripts/setup.gradle")
