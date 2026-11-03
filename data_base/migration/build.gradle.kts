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
    testImplementation(project(":data_base:migration:builder"))
    testImplementation(project(":data_base:migration:executor"))
    testImplementation(project(":data_base:operation:schema:api"))
    testImplementation(project(":data_base:operation:schema:ast"))
    testImplementation(project(":data_base:operation:schema:builder"))
    testImplementation(project(":data_base:operation:schema:params"))
    testImplementation(project(":data_base:operation:schema:renderer"))
    testImplementation(project(":data_base:operation:query:api"))
    testImplementation(project(":data_base:operation:query:ast"))
    testImplementation(project(":data_base:operation:query:builder"))
    testImplementation(project(":data_base:operation:query:executor"))
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
