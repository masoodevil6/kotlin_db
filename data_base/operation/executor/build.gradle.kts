plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:manager:execute"))
    implementation(project(":data_base:manager:connection"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
