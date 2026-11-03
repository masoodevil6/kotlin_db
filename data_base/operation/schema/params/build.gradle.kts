plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.operation.schema.params"
version = "unspecified"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
