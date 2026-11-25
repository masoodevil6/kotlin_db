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

    testImplementation(kotlin("test"))
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
val runMigrationRecovery = providers.gradleProperty("database.example.recovery")
    .map { it.toBooleanStrict() }
    .orElse(false)
val runMigrationStatus = providers.gradleProperty("database.example.status")
    .map { it.toBooleanStrict() }
    .orElse(false)
val inspectMigrationState = providers.gradleProperty("database.example.state")
    .map { it.toBooleanStrict() }
    .orElse(false)
val retryMigrationUp = providers.gradleProperty("database.example.retryUp")
    .map { it.toBooleanStrict() }
    .orElse(false)
val retryMigrationDown = providers.gradleProperty("database.example.retryDown")
    .map { it.toBooleanStrict() }
    .orElse(false)
val completeVersionOnRetry = providers.gradleProperty("database.example.retry.toCompleteVersion")
    .map { it.toBooleanStrict() }
    .orElse(false)

val localExampleEnvironment = rootProject.file(".env").takeIf { it.isFile }
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
    ?.toMap()
    .orEmpty()

// An explicitly supplied process environment variable takes precedence over the local file.
val exampleRuntimeEnvironment = localExampleEnvironment.mapValues { (name, fileValue) ->
    System.getenv(name) ?: fileValue
}

tasks.named<JavaExec>("run") {
    // Avoid persisting local database credentials in Gradle's configuration cache.
    notCompatibleWithConfigurationCache("The example run task reads credentials from the local .env file")
    environment(exampleRuntimeEnvironment)
    systemProperty("database.example.run", runDatabaseExamples.get().toString())
    systemProperty("database.example.rollback", runDestructiveRollback.get().toString())
    systemProperty("database.example.recovery", runMigrationRecovery.get().toString())
    systemProperty("database.example.status", runMigrationStatus.get().toString())
    systemProperty("database.example.state", inspectMigrationState.get().toString())
    systemProperty("database.example.retryUp", retryMigrationUp.get().toString())
    systemProperty("database.example.retryDown", retryMigrationDown.get().toString())
    systemProperty("database.example.retry.toCompleteVersion", completeVersionOnRetry.get().toString())
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
