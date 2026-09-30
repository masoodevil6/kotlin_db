plugins {
    kotlin("jvm")
    application
}

group = "gog.my_project.data_base.migration.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":data_base:core"))
    implementation(project(":data_base:manager:execute"))
    implementation(project(":data_base:migration:api"))
    implementation(project(":data_base:migration:ast"))
    implementation(project(":data_base:migration:params"))
    implementation(project(":data_base:migration:builder"))
    implementation(project(":data_base:migration:dialect"))
    implementation(project(":data_base:migration:renderer"))
    implementation(project(":data_base:migration:executor"))
}

application {
    mainClass.set("gog.my_project.data_base.migration.example.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}
