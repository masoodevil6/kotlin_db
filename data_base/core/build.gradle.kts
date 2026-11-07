plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.core"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:operation:config"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
