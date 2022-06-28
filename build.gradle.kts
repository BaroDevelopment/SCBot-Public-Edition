
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.42.0"
    id("io.realm.kotlin") version "1.0.0"
}

group = "madeby.astatio"
version = "0.9.2"

repositories {
    mavenCentral()
    maven {
        url  = uri("https://m2.dv8tion.net/releases")
        name = "m2-dv8tion"
    }
    maven {
        url = uri("https://jitpack.io/")
    }
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
}

application {
    mainClass.set("Main")
}

dependencies {

    runtimeOnly("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.6.21")
    implementation("com.facebook:ktfmt:0.38")
    implementation("org.json:json:20220320")

    //KOTLIN FOR DATA SCIENCE
    implementation("org.jetbrains.kotlinx:dataframe:0.8.0-rc-8")

    //KOTLIN COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")

    //CACHE
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.1")

    //JDA
    implementation("net.dv8tion:JDA:5.0.0-alpha.12")
    implementation("com.github.minndevelopment:jda-ktx:7c1f33a")

    //TIME
    implementation("org.ocpsoft.prettytime:prettytime:5.0.3.Final")
    implementation("org.ocpsoft.prettytime:prettytime-nlp:5.0.3.Final")

    //SYSTEM INFO
    implementation("com.github.oshi:oshi-core:6.1.6")

    //LOGGING
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("ch.qos.logback:logback-core:1.2.11")
    implementation("org.fusesource.jansi:jansi:2.4.0")

    //REALM DATABASE
    implementation("io.realm.kotlin:library-base:1.0.0")
}

tasks.jar {
    archiveFileName.set("SCBot-Kotlin${archiveVersion}.jar")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "18"
    // kotlinOptions.freeCompilerArgs = listOf("-Xuse-k2")
}

tasks.withType<ShadowJar> {
    this.isZip64 = true
}