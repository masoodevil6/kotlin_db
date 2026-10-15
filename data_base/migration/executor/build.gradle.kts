plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.migration.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":tools"))

    implementation(project(":data_base:core"))

    implementation(project(":data_base:migration:api"))
    implementation(project(":data_base:migration:ast"))
    implementation(project(":data_base:migration:builder"))
    implementation(project(":data_base:migration:dialect"))
    implementation(project(":data_base:migration:params"))
    implementation(project(":data_base:migration:renderer"))

    implementation(project(":data_base:query:api"))
    implementation(project(":data_base:query:ast"))
    implementation(project(":data_base:query:builder"))
    implementation(project(":data_base:query:dialect"))
    implementation(project(":data_base:query:renderer"))

    implementation(project(":data_base:manager:execute"))

    testImplementation(kotlin("test"))
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
