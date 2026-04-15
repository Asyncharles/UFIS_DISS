plugins {
    java
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
}

val sourceSets = the<org.gradle.api.tasks.SourceSetContainer>()

group = "com.ufis"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Datomic Pro (via Maven)
    implementation("com.datomic:peer:1.0.7187")

    // Jackson (comes with spring-boot-starter-web, but explicit for dates)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<JavaExec>("lineageBenchmark") {
    group = "verification"
    description = "Runs the phase 18 lineage benchmark harness across simulator tiers"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ufis.benchmark.LineageBenchmarkHarness")
}
