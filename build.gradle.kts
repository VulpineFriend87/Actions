plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.okaeri.cloud/releases")
    maven("https://repo.tcoded.com/releases")
    maven("https://repo.vulpine.top/repository/maven-open/")
}

dependencies {
    // Exposed on the API surface: Action reads/writes okaeri serdes types, renders
    // through commons' Colorize, and dispatches through FoliaLib's scheduler.
    api(libs.okaeri.core)
    api(libs.commons)
    api(libs.folialib)

    compileOnly(libs.paper)

    testImplementation(libs.paper)
    testImplementation(libs.okaeri.yaml.snakeyaml)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.launcher)
}

group = "top.vulpine"
version = "0.1.0"
description = "Actions"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks {
    test {
        useJUnitPlatform()
    }
}

publishing {
    repositories {
        maven {
            name = "vulpine"
            url = uri("https://repo.vulpine.top/repository/maven-open/")
            credentials(PasswordCredentials::class)
        }
    }

    publications {
        create<MavenPublication>("maven") {
            artifactId = "actions"
            from(components["java"])
        }
    }
}
