plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.query.cte"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    api(project(":collection:core"))
    api(project(":data_base:core"))
    api(project(":data_base:operation:query:api"))
    api(project(":data_base:operation:query:ast"))
    implementation(project(":data_base:operation:query:builder"))
    api(project(":data_base:operation:query:executor"))
    api(project(":data_base:manager:execute"))
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:core"))
    testImplementation(project(":data_base:operation:query:ast"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
