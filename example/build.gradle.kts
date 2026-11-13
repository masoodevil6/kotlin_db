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
    implementation(project(":data_base:migration"))
    implementation(project(":data_base:migration:api"))
    implementation(project(":data_base:migration:executor"))
    implementation(project(":data_base:operation:query:builder"))
}

application {
    mainClass.set("gog.my_project.example.MainKt")
}

val runDatabaseExamples = providers.gradleProperty("database.example.run")
    .map { it.toBooleanStrict() }
    .orElse(false)
val runDestructiveRollback = providers.gradleProperty("database.example.rollback")
    .map { it.toBooleanStrict() }
    .orElse(false)

tasks.named<JavaExec>("run") {
    systemProperty("database.example.run", runDatabaseExamples.get().toString())
    systemProperty("database.example.rollback", runDestructiveRollback.get().toString())
}

kotlin {
    jvmToolchain(24)
}
