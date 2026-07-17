import org.gradle.jvm.tasks.Jar

plugins {
    id("java")
    // Shadow 8.3.6 uses an ASM remapper that fails on Java 21 invokedynamic
    // bytecode when Gradle 9 runs the build. Shadow 9.5.1 supports Gradle 9.2+
    // and fixes that relocation path.
    id("com.gradleup.shadow") version "9.5.1"
}

group = "com.pgc"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    // PaperMC API
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API — compileOnly since the server provides this at runtime.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // GriefPrevention — compileOnly since it is a soft dependency and is
    // only required for compilation of the ClaimPermissionCheckEvent hook.
    // The jar is bundled locally under libs/ because GriefPrevention is not
    // published to a public Maven repository. Replace this with the exact
    // GriefPrevention.jar your server uses if you update GriefPrevention.
    compileOnly(files("libs/GriefPrevention.jar"))

    // Embedded and relocated into the final jar. This makes the audit and
    // exemption database work without requiring server owners to install a
    // JDBC driver separately.
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Inject the project version into plugin.yml at build time so the two
// never drift out of sync.
tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.withType<Jar> {
    archiveBaseName.set("GPBucketBypass")
    archiveClassifier.set("")
}

tasks.shadowJar {
    archiveBaseName.set("GPBucketBypass")
    archiveClassifier.set("")
    relocate("org.sqlite", "com.pgc.gpbucketbypass.libs.sqlite")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
