plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.renderer"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))

    implementation(project(":data_base:operation:query:api"))
    implementation(project(":data_base:operation:query:ast"))
    implementation(project(":data_base:operation:query:dialect"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:operation:query:builder"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
