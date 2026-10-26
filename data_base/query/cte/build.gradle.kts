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
    api(project(":data_base:query:api"))
    api(project(":data_base:query:ast"))
    implementation(project(":data_base:query:builder"))
    api(project(":data_base:query:executor"))
    api(project(":data_base:manager:execute"))
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:core"))
    testImplementation(project(":data_base:query:ast"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
