import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("com.gradleup.shadow") version "9.6.0"
}

group = "com.rumilance.practice"

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

    // ProtocolLib soft-dependency - powers the active sign-probe mod detector (MC-265322).
    // Compile against the stable API; the server must run a build that supports the target MC
    // version (1.21.11 support ships in ProtocolLib dev builds).
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")

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
    relocate("org.mariadb.jdbc", "com.rumilance.practice.libs.mariadb")
    // NOTE: org.slf4j must NOT be bundled/relocated. Paper provides SLF4J at runtime
    // (JavaPlugin#getSLF4JLogger) with a real Log4j provider; bundling our own copy
    // leaves it provider-less, causing the startup stderr noise
    // "SLF4J(W): No SLF4J providers were found / defaulting to NOP" plus a
    // "Nag author(s) ... System.out/err.print" warning. Excluding it below routes
    // HikariCP/sqlite-jdbc logging through the server's SLF4J instead.
    dependencies {
        exclude(dependency("org.slf4j:.*:.*"))
    }
    // NOTE: org.sqlite (sqlite-jdbc) must NOT be relocated. Its JNI native library
    // (libsqlitejdbc.so) binds to the exact package "org.sqlite.core.NativeDB", so
    // relocating the classes breaks the native method lookup with UnsatisfiedLinkError.
    // We rely on sqlite-jdbc's own package; no other plugin ships sqlite-jdbc by default.

    minimize {
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
    }

    // Resolve file paths at configuration time so the task is configuration-cache compatible
    // (referencing Task.project at execution time is unsupported with the configuration cache).
    val brandingIcon = layout.projectDirectory.file("src/main/resources/branding/server-icon.png")
    val libsIcon = layout.buildDirectory.file("libs/server-icon.png")
    val rootIcon = layout.projectDirectory.file("server-icon.png")
    doLast {
        val icon = brandingIcon.asFile
        if (icon.exists()) {
            icon.copyTo(libsIcon.get().asFile, overwrite = true)
            icon.copyTo(rootIcon.asFile, overwrite = true)
        } else {
            logger.warn("[Branding] branding/server-icon.png missing — ops icon not copied.")
        }
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("thin")
}
