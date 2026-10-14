plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.shoplab"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val jjwtVersion = "0.12.7"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testImplementation("com.networknt:json-schema-validator:1.5.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Tests rápidos (*Test): no necesitan Docker
tasks.test {
    useJUnitPlatform()
    exclude("**/*IT.class")
}

// Tests de integración (*IT): usan Testcontainers, necesitan Docker
val integrationTest by tasks.registering(Test::class) {
    description = "Tests de integración con Testcontainers (*IT)"
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*IT.class")
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}

// Colima (Docker en macOS sin Docker Desktop) usa su propio socket; Testcontainers no lo encuentra solo.
// Si DOCKER_HOST no está definido y existe el socket de colima, se lo pasamos a los tests.
val colimaSocket = file("${System.getProperty("user.home")}/.colima/default/docker.sock")
if (System.getenv("DOCKER_HOST") == null && colimaSocket.exists()) {
    tasks.withType<Test>().configureEach {
        environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
}

// Un solo jar ejecutable con nombre fijo, para que el Dockerfile sepa cuál copiar
tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.jar {
    enabled = false
}
