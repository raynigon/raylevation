import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.Kapt
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.OffsetDateTime

plugins {
    jacoco
    id("idea")
    id("groovy")
    id("jacoco")
    id("signing")
    id("maven-publish")

    id("org.springframework.boot") version "4.0.6"
    id("org.springframework.cloud.contract") version "5.0.3"
    id("io.spring.dependency-management") version "1.1.7"

    // Auto Release
    id("net.researchgate.release") version "3.1.0"

    // Dependency Check
    id("org.owasp.dependencycheck") version "12.1.3"

    // Docker Container Build
    id("com.google.cloud.tools.jib") version "3.5.3"

    // Linter
    id("pmd")
    id("checkstyle")
    id("com.github.spotbugs") version "6.4.4"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"

    // Kotlin
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    kotlin("plugin.jpa") version "2.3.10"
    kotlin("kapt") version "2.3.10"

    // Dokka
    id("org.jetbrains.dokka") version "2.1.0"
    id("com.dorongold.task-tree") version "4.0.1"
}

group = "com.raynigon.raylevation"
java.sourceCompatibility = JavaVersion.VERSION_21

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
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
    // Required at runtime by ecs-logging-access (RestClientCustomizer)
    implementation("org.springframework.boot:spring-boot-restclient")

    // unit-api
    implementation("com.raynigon.unit-api:spring-boot-jackson-starter:4.0.1")
    implementation("com.raynigon.unit-api:spring-boot-jpa-starter:4.0.1")
    implementation("com.raynigon.unit-api:unit-api-kotlin:4.0.1")

    // Logging
    implementation("com.raynigon.spring-boot:ecs-logging-app:4.0.1")
    implementation("com.raynigon.spring-boot:ecs-logging-access:4.0.1")
    implementation("com.raynigon.spring-boot:ecs-logging-async:4.0.1")

    // Helpers
    implementation("org.apache.sis.storage:sis-geotiff:1.7")
    implementation("com.github.davidmoten:rtree:0.12")
    implementation("com.github.davidmoten:geo:0.8.1")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.6")

    // Spock
    testImplementation("org.apache.groovy:groovy:5.0.5")
    testImplementation(platform("org.spockframework:spock-bom:2.4-groovy-5.0"))
    testImplementation("org.spockframework:spock-core")
    testImplementation("org.spockframework:spock-spring")

    // Spring
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")

    // Testcontainers
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:spock")

    // Mockk
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.github.hakky54:logcaptor:2.12.5")

    // Documentation
    testImplementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    testImplementation("com.raynigon.unit-api:spring-boot-springdoc-starter:4.0.1")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.4")
        mavenBom("org.springframework.cloud:spring-cloud-contract-dependencies:5.0.3")
    }
}

// Smoke tests hit real external services (production tile download host, open-elevation.com).
// They are intentionally kept out of `test`/`check` and are only run via `./gradlew smokeTest`.
sourceSets {
    create("smokeTest") {
        groovy.srcDir("src/smokeTest/groovy")
        resources.srcDir("src/smokeTest/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += output + compileClasspath
    }
}

val smokeTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}
configurations.getByName("smokeTestRuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
}

allOpen {
    annotation("org.springframework.stereotype.Service")
    annotation("javax.persistence.Entity")
}

contracts {
    failOnNoContracts.set(true)

    includedFiles.set(
        listOf(
            "v1/**/*.yaml",
        ),
    )

    ignoredFiles.set(emptyList())

    testFramework.set(org.springframework.cloud.contract.verifier.config.TestFramework.JUNIT5)
    val namespace = "com.raynigon.raylevation.contracts"

    baseClassMappings.baseClassMapping(
        "v1\\.lookup",
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
    named("runKtlintCheckOverContractTestSourceSet") {
        dependsOn("generateContractTests")
    }
    withType<Test> {
        useJUnitPlatform()

        if (name == "test") {
            testClassesDirs +=
                sourceSets.contractTest
                    .get()
                    .output
                    .classesDirs
            classpath +=
                sourceSets.contractTest
                    .get()
                    .runtimeClasspath

            finalizedBy(withType<JacocoReport>()) // report is always generated after tests run
        }
    }
    register<Test>("smokeTest") {
        description = "Runs smoke tests against real external services (network, slow, not part of `test`/`check`)."
        group = "verification"
        testClassesDirs = sourceSets["smokeTest"].output.classesDirs
        classpath = sourceSets["smokeTest"].runtimeClasspath
        // Apache SIS 1.7 violates one of its own internal assertions (lock ownership in
        // ImageFileDirectory.getSampleModel) on the direct read path, disable assertions for SIS only.
        // Same reason this is disabled for the `test` task in gradle/scripts/test.gradle
        jvmArgs("-da:org.apache.sis...")
        outputs.upToDateWhen { false } // always re-run; it's about hitting the real endpoints
        // Optional override of app.raylevation.srtm.url in Spring's env format (GitHub secret in CI),
        // forward it explicitly from the Gradle process' environment into the forked test JVM.
        // Without it, the default from application.yml is used.
        System.getenv("APP_RAYLEVATION_SRTM_URL")?.takeIf { it.isNotBlank() }?.let { environment("APP_RAYLEVATION_SRTM_URL", it) }
    }
    withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            jvmTarget.set(JvmTarget.JVM_21)
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
    withType<com.github.spotbugs.snom.SpotBugsTask> {
        // SpotBugs findings are tracked via Codacy's own SpotBugs scan (see ci-main.yml),
        // which is configured to never fail the build. Mirror that here.
        ignoreFailures = true
    }
}

jib {
    val commitHash: String = System.getenv("COMMIT_HASH") ?: "unknown_commit"
    from {
        image = "ghcr.io/raynigon/raylevation-base:21"
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
