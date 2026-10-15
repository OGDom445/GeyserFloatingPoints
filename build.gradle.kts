// Modified by OGDom445 on 2026-10-15: unofficial compatibility patch. GNU GPL v3; see LICENSE.
plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "oxy.geyser.fp"
version = "2.1.1-patched"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("org.geysermc.geyser:core:${providers.gradleProperty("geyserVersion").getOrElse("2.12.0-SNAPSHOT")}") {
        exclude(group = "com.google.code.gson", module = "gson")
    }

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("org.cloudburstmc.fastutil.commons:long-common:8.5.15")
    implementation("org.cloudburstmc.fastutil.maps:long-object-maps:8.5.15")

}

tasks {
    shadowJar {
        archiveFileName = "geyserfloatingpoints.jar"

        from(rootProject.file("LICENSE")) {
            into("META-INF")
            rename { "LICENSE-GeyserFloatingPoints.txt" }
        }
        from(rootProject.file("NOTICE.txt")) {
            into("META-INF")
            rename { "NOTICE-GeyserFloatingPoints.txt" }
        }

        relocate("org.yaml.snakeyaml", "oxy.geyser.fp.shaded.snakeyaml")
        relocate("com.fasterxml", "oxy.geyser.fp.shaded.fasterxml")
        relocate("it.unimi.dsi.fastutil", "oxy.geyser.fp.shaded.fastutil")
    }
}

tasks.register("listBundledDependencies") {
    doLast {
        configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.forEach {
            println("BUNDLED ${it.moduleVersion.id.group}:${it.name}:${it.moduleVersion.id.version}")
        }
    }
}
