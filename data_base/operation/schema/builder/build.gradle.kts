plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.schema.builder"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:core"))
    api(project(":data_base:operation:schema:params"))
    api(project(":data_base:operation:schema:ast"))
    api(project(":data_base:operation:schema:api"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
