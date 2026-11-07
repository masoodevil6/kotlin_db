plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.migration.core"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))
    api(project(":data_base:operation:schema"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(24)
}
