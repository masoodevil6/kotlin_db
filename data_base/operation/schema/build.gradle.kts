plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.schema"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:operation:executor"))
    api(project(":data_base:manager:execute"))
    api(project(":data_base:manager:connection"))
    implementation(project(":data_base:core"))
    implementation(project(":data_base:operation:config"))
    implementation(project(":data_base:operation:schema:api"))
    implementation(project(":data_base:operation:schema:builder"))
    implementation(project(":data_base:operation:schema:executor"))
    implementation(project(":data_base:operation:schema:params"))
    implementation(project(":data_base:operation:schema:renderer"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
