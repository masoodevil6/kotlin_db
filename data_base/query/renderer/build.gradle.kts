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

    implementation(project(":data_base:query:api"))
    implementation(project(":data_base:query:ast"))
    implementation(project(":data_base:query:dialect"))
    implementation(project(":data_base:query:meta_data"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:query:builder"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
