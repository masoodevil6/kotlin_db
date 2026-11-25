plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base_session.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:operation:session:api"))
    api(project(":data_base:operation:session:ast"))
    api(project(":data_base:operation:session:dialect"))
    implementation(project(":data_base:operation:session:renderer"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
