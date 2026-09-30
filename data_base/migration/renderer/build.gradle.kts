plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.migration.renderer"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":data_base:core"))
    implementation(project(":data_base:migration:params"))
    implementation(project(":data_base:migration:ast"))
    implementation(project(":data_base:migration:api"))
    implementation(project(":data_base:migration:dialect"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}