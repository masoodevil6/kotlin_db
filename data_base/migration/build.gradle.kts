plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.migration"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    testImplementation(project(":data_base:core"))
    testImplementation(project(":data_base:manager:execute"))
    testImplementation(project(":data_base:migration:api"))
    testImplementation(project(":data_base:migration:ast"))
    testImplementation(project(":data_base:migration:builder"))
    testImplementation(project(":data_base:migration:params"))
    testImplementation(project(":data_base:migration:executor"))
    testImplementation(project(":data_base:query:api"))
    testImplementation(project(":data_base:query:ast"))
    testImplementation(project(":data_base:query:builder"))
    testImplementation(project(":data_base:query:executor"))
}

val migrationTestDatabaseProperties = listOf(
    "migration.test.db.domain",
    "migration.test.db.port",
    "migration.test.db.name",
    "migration.test.db.username",
    "migration.test.db.password",
    "migration.test.db.refresh",
    "help.runtime.runId",
    "help.runtime.path",
)

tasks.test {
    useJUnitPlatform()
    migrationTestDatabaseProperties.forEach { propertyName ->
        providers.gradleProperty(propertyName).orNull?.let { propertyValue ->
            systemProperty(propertyName, propertyValue)
        }
    }
}
kotlin {
    jvmToolchain(24)
}
