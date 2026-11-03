plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.meta_data"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:operation:query:ast"))
    implementation(project(":data_base:operation:query:api"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
