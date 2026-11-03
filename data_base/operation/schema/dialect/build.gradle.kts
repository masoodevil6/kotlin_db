plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.schema.dialect"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:operation:schema:ast"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
