import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    application
    kotlin("jvm") version "1.9.21"

    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "graphics.scenery"
version = "0.2.0-SNAPSHOT"
description = "LiV-renderer"

repositories {
    maven("https://maven.scijava.org/content/groups/public")
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // you can use either Git hashes here to identify a version,
    // or version tags from https://github.com/scenerygraphics/scenery/releases
    api("graphics.scenery:scenery:0.9.2")

    // necessary for logging to work correctly, adjust to the logging
    // framework of your liking
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")
}

application {
    mainClass ="graphics.scenery.DistributedVolumeRenderer"
}

tasks {
    withType<KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = project.properties["jvmTarget"]?.toString() ?: "21"
            freeCompilerArgs += listOf("-Xinline-classes", "-opt-in=kotlin.RequiresOptIn")
        }
    }

    withType<JavaCompile>().all {
        targetCompatibility = project.properties["jvmTarget"]?.toString() ?: "21"
        sourceCompatibility = project.properties["jvmTarget"]?.toString() ?: "21"
    }

    task<Jar>("testJar") {
        archiveClassifier.set("tests")
        from(sourceSets.test.get().output)
        dependsOn("assemble")
    }

    named<ShadowJar>("shadowJar") {
        isZip64 = true
    }

    named<Test>("test") {
        useJUnitPlatform()
    }
}
