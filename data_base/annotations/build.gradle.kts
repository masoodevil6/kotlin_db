plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.annotations"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {


    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}