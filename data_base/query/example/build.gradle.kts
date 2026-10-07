plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.example"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":tools"))

    implementation(project(":data_base:core"))
    implementation(project(":data_base:models:eloquent"))

    implementation(project(":data_base:manager:execute"))

    implementation(project(":data_base:query:api"))
    implementation(project(":data_base:query:ast"))
    implementation(project(":data_base:query:builder"))
    implementation(project(":data_base:query:renderer"))
    implementation(project(":data_base:query:executor"))
}

tasks.test {
    useJUnitPlatform()
}

val queryExampleVersion = providers.gradleProperty("query.example.version")
val runSelect = providers.gradleProperty("query.example.run.select").map { it.toBooleanStrict() }.orElse(true)
val runInsert = providers.gradleProperty("query.example.run.insert").map { it.toBooleanStrict() }.orElse(false)
val runUpdate = providers.gradleProperty("query.example.run.update").map { it.toBooleanStrict() }.orElse(false)
val runDelete = providers.gradleProperty("query.example.run.delete").map { it.toBooleanStrict() }.orElse(false)

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the configured Query Builder examples."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("gog.my_project.data_base.query.example.MainKt")
    queryExampleVersion.orNull?.let { args(it) }
    systemProperty("query.example.run.select", runSelect.get().toString())
    systemProperty("query.example.run.insert", runInsert.get().toString())
    systemProperty("query.example.run.update", runUpdate.get().toString())
    systemProperty("query.example.run.delete", runDelete.get().toString())
}

kotlin {
    jvmToolchain(24)
}
