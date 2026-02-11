plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":tools"))

    implementation(project(":data_base:core"))

    implementation(project(":data_base:query:ast"))
    implementation(project(":data_base:query:builder"))
    implementation(project(":data_base:query:api"))
    implementation(project(":data_base:query:dialect"))
    implementation(project(":data_base:query:renderer"))

    implementation(project(":data_base:manager:execute"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}