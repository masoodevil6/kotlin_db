plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:operation:executor"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
