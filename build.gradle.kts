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
    // Exposed on the API surface: Action reads/writes okaeri serdes types and
    // renders through commons' Colorize.
    api(libs.okaeri.core)
    api(libs.commons)

    compileOnly(libs.paper)

    // Optional adapter only; consumers that use FoliaLib provide it themselves.
    compileOnly(libs.folialib)

    testImplementation(libs.paper)
    testImplementation(libs.folialib)
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
