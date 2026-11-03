plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.migration.builder"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:migration:api"))
    api(project(":data_base:operation:schema:api"))
    implementation(project(":data_base:operation:schema:builder"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
