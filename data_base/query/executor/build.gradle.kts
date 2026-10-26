plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.query.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {

    api(project(":collection:core"))

    implementation(project(":tools"))

    implementation(project(":data_base:core"))

    implementation(project(":data_base:query:ast"))
    implementation(project(":data_base:query:builder"))
    implementation(project(":data_base:query:api"))
    implementation(project(":data_base:query:meta_data"))
    implementation(project(":data_base:query:dialect"))
    implementation(project(":data_base:query:renderer"))

    implementation(project(":data_base:manager:execute"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:query:cte"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
