plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.manager"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:core"))
    api(project(":data_base:manager:connection"))
    api(project(":data_base:manager:execute"))
    api(project(":data_base:operation:query:executor"))
    api(project(":data_base:operation:schema"))
    api(project(":data_base:migration:api"))
    api(project(":data_base:migration:builder"))

    implementation(project(":data_base:migration:executor"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
