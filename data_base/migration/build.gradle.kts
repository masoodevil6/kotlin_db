plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.migration"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:migration:executor"))
    api(project(":data_base:manager"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
