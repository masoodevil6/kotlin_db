plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.migration.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":tools"))

    api(project(":data_base:core"))
    api(project(":data_base:manager:connection"))
    api(project(":data_base:manager:execute"))
    api(project(":data_base:migration:api"))
    api(project(":data_base:operation:schema:api"))
    api(project(":data_base:operation:schema:renderer"))

    implementation(project(":data_base:operation:query:api"))
    implementation(project(":data_base:operation:query:ast"))
    implementation(project(":data_base:operation:query:builder"))
    implementation(project(":data_base:operation:query:dialect"))
    implementation(project(":data_base:operation:query:renderer"))

    implementation(project(":data_base:operation:executor"))
    implementation(project(":data_base:operation:schema:executor"))
    implementation(project(":data_base:operation:schema:builder"))
    implementation(project(":data_base:operation:schema:dialect"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:migration:builder"))
}

tasks.test {
    useJUnitPlatform()

    val integrationTestEnvironment = mapOf(
        "KOTLIN_DB_MIGRATION_TEST_HOST" to "help.migration.mysql.host",
        "KOTLIN_DB_MIGRATION_TEST_PORT" to "help.migration.mysql.port",
        "KOTLIN_DB_MIGRATION_TEST_DATABASE" to "help.migration.mysql.database",
        "KOTLIN_DB_MIGRATION_TEST_USERNAME" to "help.migration.mysql.username",
        "KOTLIN_DB_MIGRATION_TEST_PASSWORD" to "help.migration.mysql.password",
    )
    integrationTestEnvironment.forEach { (environmentName, propertyName) ->
        providers.gradleProperty(propertyName).orNull?.let { value ->
            environment(environmentName, value)
        }
    }
}
kotlin {
    jvmToolchain(24)
}
