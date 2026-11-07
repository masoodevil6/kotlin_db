plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.schema.renderer"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:core"))
    api(project(":data_base:operation:config"))
    api(project(":data_base:operation:schema:ast"))
    api(project(":data_base:operation:schema:dialect"))
    implementation(project(":data_base:operation:schema:params"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
