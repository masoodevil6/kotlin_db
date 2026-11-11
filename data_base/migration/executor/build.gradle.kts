plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.migration.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:manager:execute"))
    api(project(":data_base:operation:executor"))
    api(project(":data_base:migration:api"))
    api(project(":data_base:migration:core"))

    implementation(project(":data_base:operation:schema"))
    implementation(project(":data_base:operation:query:executor"))
    implementation(project(":data_base:operation:query:builder"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:manager"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
