plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.config"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
