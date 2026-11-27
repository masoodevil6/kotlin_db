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
    api(project(":data_base:manager:execute"))
    api(project(":data_base:operation:executor"))
    api(project(":data_base:migration:api"))
    api(project(":data_base:migration:core"))

    implementation(project(":data_base:operation:schema"))
    implementation(project(":data_base:operation:query:executor"))
    implementation(project(":data_base:operation:query:builder"))
    implementation(libs.kotlinxSerialization)

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:manager"))
    testImplementation(project(":data_base:migration"))
    testImplementation(libs.kotlinxSerialization)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("mysql-integration")
    }
}

val migrationMysqlIntegrationEnvironmentKeys = listOf(
    "KOTLIN_DB_MIGRATION_IT_DOMAIN",
    "KOTLIN_DB_MIGRATION_IT_PORT",
    "KOTLIN_DB_MIGRATION_IT_SCHEMA",
    "KOTLIN_DB_MIGRATION_IT_ALLOWED_SCHEMAS",
    "KOTLIN_DB_MIGRATION_IT_USER",
    "KOTLIN_DB_MIGRATION_IT_PASSWORD",
)

tasks.register<org.gradle.api.tasks.testing.Test>("mysqlIntegrationTest") {
    group = "verification"
    description = "Runs tagged real MariaDB Migration tests against an explicitly allowlisted disposable schema."
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("mysql-integration")
    }
    maxParallelForks = 1
    testLogging.showStandardStreams = true
    notCompatibleWithConfigurationCache(
        "The Migration MariaDB integration task reads dedicated local database credentials from .env",
    )
    doFirst {
        val requiredNames = migrationMysqlIntegrationEnvironmentKeys.toSet()
        val localEnvironment = rootProject.file(".env").takeIf { it.isFile }
            ?.readLines()
            ?.mapIndexedNotNull { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    null
                } else {
                    val separator = line.indexOf('=')
                    require(separator > 0) { "Invalid .env entry on line " + (index + 1) }
                    val name = line.substring(0, separator).trim()
                    require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                        "Invalid .env variable name on line " + (index + 1)
                    }
                    if (name !in requiredNames) {
                        null
                    } else {
                        val rawValue = line.substring(separator + 1).trim()
                        val value = if (
                            rawValue.length >= 2 &&
                            ((rawValue.first() == '\"' && rawValue.last() == '\"') ||
                                (rawValue.first() == '\'' && rawValue.last() == '\''))
                        ) {
                            rawValue.substring(1, rawValue.length - 1)
                        } else {
                            rawValue
                        }
                        name to value
                    }
                }
            }
            ?.toMap()
            .orEmpty()
        val integrationEnvironment = migrationMysqlIntegrationEnvironmentKeys.mapNotNull { name ->
            (System.getenv(name) ?: localEnvironment[name])?.let { name to it }
        }.toMap()
        val missingNames = migrationMysqlIntegrationEnvironmentKeys.filter {
            integrationEnvironment[it].isNullOrEmpty()
        }
        require(missingNames.isEmpty()) {
            "Missing required dedicated Migration MariaDB integration settings: " + missingNames.joinToString()
        }
        environment(integrationEnvironment)
    }
}

kotlin {
    jvmToolchain(24)
}
