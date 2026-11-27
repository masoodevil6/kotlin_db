plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base_session.dialect"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:dialect"))
    api(project(":data_base:operation:session:ast"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
