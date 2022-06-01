import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.21"
    id("me.champeau.jmh") version "0.6.6"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "org.eiennohito"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.worksap.nlp:sudachi:0.5.3")
    testImplementation("com.worksap.nlp:sudachi:0.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-html:0.7.5")
    implementation("com.github.luben:zstd-jni:1.5.2-3")
    implementation("com.google.guava:guava:31.1-jre")
    testImplementation(kotlin("test"))
    jmh("org.openjdk.jmh:jmh-core:1.35")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.35")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
}