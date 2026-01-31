plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.definition"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}