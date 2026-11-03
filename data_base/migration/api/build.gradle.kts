plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.migration.api"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:operation:schema:api"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
