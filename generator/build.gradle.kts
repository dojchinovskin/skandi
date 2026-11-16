import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Only the JSON tree API is used (no serialization compiler plugin), so mapping is explicit.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("mk.skandi.generator.MainKt")
}

// Run from the repo root so paths like data/site/puzzles work the same everywhere.
tasks.named<JavaExec>("run") {
    workingDir = rootDir.parentFile
}

// `./gradlew check` also validates every published puzzle.
val validatePuzzles by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates all puzzles in data/site/puzzles against format/README.md."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootDir.parentFile
    args("validate", "data/site/puzzles")
}
tasks.named("check") { dependsOn(validatePuzzles) }

// `./gradlew check` also checks the word list (data/words/README.md).
val checkWords by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks data/words/words.tsv against data/words/README.md."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootDir.parentFile
    args("words-check", "data/words/words.tsv")
}
tasks.named("check") { dependsOn(checkWords) }
