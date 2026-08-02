import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("com.gradleup.shadow") version "9.6.0"
}

group = "com.rumilance.practice"
version = "1.2.0"

description = "RumilancePractice - competitive practice/duel plugin for Paper servers"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configurations {
    // Let tests see the same compile-time-only API (Paper API, WorldEdit) that main sources see,
    // so pure-JUnit tests can exercise classes built on top of e.g. YamlConfiguration without
    // needing a running server.
    testImplementation.get().extendsFrom(compileOnly.get())
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "enginehub"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "sonatype-oss-snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

val hikariVersion = "7.1.0"
val sqliteVersion = "3.53.2.0"
val mariadbVersion = "3.5.9"
val worldeditVersion = "7.3.0"
val junitVersion = "6.1.2"

dependencies {
    // Paper API - provided by the server at runtime
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // WorldEdit / FastAsyncWorldEdit soft-dependency - only used if present on server
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:$worldeditVersion") {
        // Avoid pulling transitive Bukkit/Paper implementations that would conflict with Paper's own.
        isTransitive = true
    }

    // Relational database access - shaded into the plugin jar and relocated to avoid classpath clashes.
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    implementation("org.mariadb.jdbc:mariadb-java-client:$mariadbVersion")

    // Testing
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("RumilancePractice")

    // Relocate shaded libraries to avoid classpath collisions with other plugins
    // that may bundle different versions of the same libraries.
    relocate("com.zaxxer.hikari", "com.rumilance.practice.libs.hikari")
    relocate("org.sqlite", "com.rumilance.practice.libs.sqlite")
    relocate("org.mariadb.jdbc", "com.rumilance.practice.libs.mariadb")
    relocate("org.slf4j", "com.rumilance.practice.libs.slf4j")

    minimize {
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("thin")
}
