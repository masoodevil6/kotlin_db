plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base_schema.example"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))
    implementation(project(":data_base:manager:execute"))
    implementation(project(":data_base:operation:schema:api"))
    implementation(project(":data_base:operation:schema:params"))
    implementation(project(":data_base:operation:schema:builder"))
    implementation(project(":data_base:operation:schema:renderer"))
    implementation(project(":data_base:operation:schema:executor"))
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Renders the direct Schema operation examples without connecting to a database."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("gog.my_project.data_base_schema.example.MainKt")
}

kotlin {
    jvmToolchain(24)
}
