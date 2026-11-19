plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.failure.spi"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinxSerialization)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
