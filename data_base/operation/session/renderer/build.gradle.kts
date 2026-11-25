plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base_session.renderer"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:operation:session:ast"))
    implementation(project(":data_base:operation:session:dialect"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
