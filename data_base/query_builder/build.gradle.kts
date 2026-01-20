plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query_builder"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:query_builder:query"))
    implementation(project(":data_base:query_builder:dialect"))
    implementation(project(":data_base:query_builder:renderer"))
    implementation(project(":data_base:query_builder:examples"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}