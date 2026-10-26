plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

group = "gog.my_project.collection"
version = ""

dependencies {
    implementation(project(":collection:core"))
}

application {
    mainClass = "gog.my_project.collection.examples.MainKt"
}

val runAddExample = providers.gradleProperty("collection.example.run.add")
    .map { it.toBooleanStrict() }
    .orElse(true)
val runForEachExample = providers.gradleProperty("collection.example.run.foreach")
    .map { it.toBooleanStrict() }
    .orElse(true)
val runFilterExample = providers.gradleProperty("collection.example.run.filter")
    .map { it.toBooleanStrict() }
    .orElse(true)
val runSizeExample = providers.gradleProperty("collection.example.run.size")
    .map { it.toBooleanStrict() }
    .orElse(true)
val runMapExample = providers.gradleProperty("collection.example.run.map")
    .map { it.toBooleanStrict() }
    .orElse(true)
val runFirstOrNullExample = providers.gradleProperty("collection.example.run.first-or-null")
    .map { it.toBooleanStrict() }
    .orElse(true)
val runToListExample = providers.gradleProperty("collection.example.run.to-list")
    .map { it.toBooleanStrict() }
    .orElse(true)
val runGetExample = providers.gradleProperty("collection.example.run.get")
    .map { it.toBooleanStrict() }
    .orElse(true)

tasks.named<JavaExec>("run") {
    systemProperty("collection.example.run.add", runAddExample.get().toString())
    systemProperty("collection.example.run.foreach", runForEachExample.get().toString())
    systemProperty("collection.example.run.filter", runFilterExample.get().toString())
    systemProperty("collection.example.run.size", runSizeExample.get().toString())
    systemProperty("collection.example.run.map", runMapExample.get().toString())
    systemProperty("collection.example.run.first-or-null", runFirstOrNullExample.get().toString())
    systemProperty("collection.example.run.to-list", runToListExample.get().toString())
    systemProperty("collection.example.run.get", runGetExample.get().toString())
}
