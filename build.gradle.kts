import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

import java.security.MessageDigest

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
val luckPermsVersion = "5.4"
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

    // LuckPerms soft-dependency - GSit permission bridge (strip GSit.*, grant GSit.SitClick on
    // join). API only: the server provides the implementation, so nothing is shaded.
    compileOnly("net.luckperms:api:$luckPermsVersion")

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
    // CI greps the build log for diagnostics; the pre-existing [removal] deprecation
    // warnings flood the annotation budget and push real errors out of view.
    options.compilerArgs.add("-nowarn")
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
    // Always run the failure report, even (especially) when the tests failed.
    finalizedBy("reportTestFailures")
}

/**
 * Gradle's own test logging splits "<Class> > <method>() FAILED" from the assertion line, so a CI
 * annotation can end up saying only "expected: <2> but was: <1>" with no clue which test it came
 * from (the raw log and the artifacts are not always reachable). This task parses the JUnit XML
 * and fails the build with ONE line per failing test that contains the class, the method, the
 * assertion message and the first source line of the stack - text the CI grep is guaranteed to
 * pick up ("Caused by:", "expected:", "but was:", ".java:NNN:").
 */
tasks.register("reportTestFailures") {
    val resultDir = layout.buildDirectory.dir("test-results/test")
    onlyIf { resultDir.get().asFile.isDirectory }
    doLast {
        val failures = mutableListOf<String>()
        resultDir.get().asFile
            .listFiles { file -> file.isFile && file.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val root = groovy.xml.XmlSlurper().parse(file)
                root."testcase".forEach { testcase ->
                    listOf("failure", "error").forEach { kind ->
                        testcase."$kind".forEach { problem ->
                            val message = problem.@message.text().trim()
                                .lineSequence().firstOrNull { it.isNotBlank() } ?: ""
                            val trace = problem.text().lineSequence()
                                .firstOrNull { it.contains(".java:") }?.trim() ?: ""
                            failures += testcase.@classname.text() + " > " +
                                    testcase.@name.text() + " FAILED -> " + message +
                                    (if (trace.isEmpty()) "" else " @ " + trace)
                        }
                    }
                }
            }
        if (failures.isEmpty()) {
            return@doLast
        }
        failures.take(12).forEach { logger.error("TESTFAILURE {}", it) }
        // Public API exception on purpose: its message is printed in the build's
        // "Caused by:" chain, which is exactly what the CI grep turns into annotations.
        throw GradleException(
                "There were failing tests: " + failures.take(12).joinToString(" ; ").take(1800))
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
    dependsOn(tasks.named("resourcePackZip"))
}

// Packages resourcepack/ into build/libs/RumilanceResourcePack.zip with pack.mcmeta at the
// archive root (the layout the Minecraft client expects). The sha1 printed here (also written
// next to the zip as RumilanceResourcePack.sha1) is what belongs into server.properties'
// resource-pack-sha1 — recompute it whenever the zip contents change.
tasks.register<Zip>("resourcePackZip") {
    group = "build"
    description = "Zips resourcepack/ (pack.mcmeta at the root) for server distribution."
    from(layout.projectDirectory.dir("resourcepack"))
    archiveFileName.set("RumilanceResourcePack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    doLast {
        val zipFile = archiveFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-1").digest(zipFile.readBytes())
        val sha1 = digest.joinToString("") { b: Byte -> "%02x".format(b) }
        zipFile.resolveSibling("RumilanceResourcePack.sha1").writeText(sha1 + "\n")
        logger.lifecycle("[resourcepack] ${zipFile.name} sha1=$sha1")
    }
}

tasks.jar {
    archiveClassifier.set("thin")
}
