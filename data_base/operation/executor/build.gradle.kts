plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:manager:execute"))
    api(project(":data_base:core"))
    api(project(":data_base:operation:config"))
    api(project(":data_base:manager:connection"))
    api(project(":data_base:operation:failure-spi"))
    api(project(":data_base:operation:session:api"))
    implementation(project(":data_base:operation:session:executor"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:operation:session:builder"))
}

tasks.test {
    useJUnitPlatform {
        excludeTags("mysql-integration")
    }
}

val mysqlIntegrationEnvironmentKeys = listOf(
    "MAIN_DB_DOMAIN",
    "MAIN_DB_PORT",
    "MAIN_DB_NAME",
    "MAIN_DB_USER",
    "MAIN_DB_PASSWORD",
)

tasks.register<org.gradle.api.tasks.testing.Test>("mysqlIntegrationTest") {
    group = "verification"
    description = "Runs real Session advisory-lock tests against the configured local MySQL-compatible server."
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("mysql-integration")
    }
    maxParallelForks = 1
    notCompatibleWithConfigurationCache(
        "The MySQL integration task reads local database credentials from .env",
    )
    doFirst {
        val requiredNames = mysqlIntegrationEnvironmentKeys.toSet()
        val localEnvironment = rootProject.file(".env").takeIf { it.isFile }
            ?.readLines()
            ?.mapIndexedNotNull { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    null
                } else {
                    val separator = line.indexOf('=')
                    require(separator > 0) { "Invalid .env entry on line ${index + 1}" }
                    val name = line.substring(0, separator).trim()
                    require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                        "Invalid .env variable name on line ${index + 1}"
                    }
                    if (name !in requiredNames) {
                        null
                    } else {
                        val rawValue = line.substring(separator + 1).trim()
                        val value = if (
                            rawValue.length >= 2 &&
                            ((rawValue.first() == '"' && rawValue.last() == '"') ||
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
        val integrationEnvironment = mysqlIntegrationEnvironmentKeys.mapNotNull { name ->
            (System.getenv(name) ?: localEnvironment[name])?.let { name to it }
        }.toMap()
        val missingNames = mysqlIntegrationEnvironmentKeys.filter { integrationEnvironment[it].isNullOrEmpty() }
        require(missingNames.isEmpty()) {
            "Missing required Session integration settings: ${missingNames.joinToString()}"
        }
        environment(integrationEnvironment)
    }
}

kotlin {
    jvmToolchain(24)
}
