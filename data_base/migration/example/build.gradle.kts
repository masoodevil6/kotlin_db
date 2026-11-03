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
    implementation(project(":data_base:migration:builder"))
    implementation(project(":data_base:migration:executor"))
    implementation(project(":data_base:operation:schema:api"))
    implementation(project(":data_base:operation:schema:builder"))
    implementation(project(":data_base:operation:schema:dialect"))
    implementation(project(":data_base:operation:schema:params"))
    implementation(project(":data_base:operation:schema:renderer"))
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
