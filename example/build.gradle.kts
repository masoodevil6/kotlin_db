plugins {
    kotlin("jvm")
    application
}

group = "gog.my_project.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:manager"))
    implementation(project(":data_base:migration:api"))
}

application {
    mainClass.set("gog.my_project.example.MainKt")
}

val runDatabaseExamples = providers.gradleProperty("database.example.run")
    .map { it.toBooleanStrict() }
    .orElse(false)

tasks.named<JavaExec>("run") {
    systemProperty("database.example.run", runDatabaseExamples.get().toString())
}

kotlin {
    jvmToolchain(24)
}
