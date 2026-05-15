plugins {
    id("java-library")
    // Add this for bundling dependencies like OkHttp
    id("com.gradleup.shadow") version "8.3.0"
}

group = "com.kobosh"
version = "1.0.0"
description = "Cardinal - A story-driven Folia plugin"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/") // Recommended for Folia
}

dependencies {
    compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT") // Use the Folia-specific API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version,
            "description" to (project.description ?: "No description provided")
        )
        inputs.properties(props)
        filteringCharset = "UTF-8" // Force UTF-8 to avoid special character errors

        filesMatching("plugin.yml") {
            expand(props)
        }
    }
    
    // Ensures you use the "shadowJar" instead of the standard "jar"
    build {
        dependsOn(shadowJar)
    }
}
