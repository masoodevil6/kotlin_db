plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.dialect"
version = "unspecified"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(24)
}
