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
    api(project(":data_base:operation:query:api"))
    api(project(":data_base:operation:query:ast"))
    api(project(":data_base:operation:query:cte"))
    implementation(project(":data_base:operation:query:builder"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:operation:query:dialect"))
    testImplementation(project(":data_base:operation:query:renderer"))
}

kotlin {
    jvmToolchain(24)
}
