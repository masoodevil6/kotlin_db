plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base_session.builder"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:operation:session:api"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
