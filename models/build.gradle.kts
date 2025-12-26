plugins {
    id("java")
    kotlin("jvm")
}

group = "gog.my_project"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":annotations"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.20")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}