plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query_builder.renderer"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:connection"))
    implementation(project(":data_base:query_builder:dialect"))
    implementation(project(":data_base:query_builder:query"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}