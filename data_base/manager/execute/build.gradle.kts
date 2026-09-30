plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.manager.execute"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:manager:connection"))
    implementation(project(":data_base:core"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}