plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base_schema.example"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:operation:schema"))
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
