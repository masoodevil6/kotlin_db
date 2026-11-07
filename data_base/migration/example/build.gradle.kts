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
    implementation(project(":data_base:migration:api"))
    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:operation:schema:ast"))
    testImplementation(project(":data_base:operation:schema:builder"))
    testImplementation(project(":data_base:operation:schema:params"))
    testImplementation(project(":data_base:operation:schema:renderer"))

    implementation(project(":data_base:operation:schema"))
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
