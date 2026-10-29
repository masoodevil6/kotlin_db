plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.query.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {

    api(project(":collection:core"))

    implementation(project(":tools"))

    api(project(":data_base:core"))

    api(project(":data_base:query:ast"))
    implementation(project(":data_base:query:builder"))
    api(project(":data_base:query:api"))
    implementation(project(":data_base:query:meta_data"))
    implementation(project(":data_base:query:dialect"))
    implementation(project(":data_base:query:renderer"))

    api(project(":data_base:manager:execute"))
    implementation(project(":data_base:manager:connection"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:query:cte"))
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    listOf(
        "execute.test.db.transaction.enabled",
        "execute.test.db.host",
        "execute.test.db.port",
        "execute.test.db.name",
        "execute.test.db.username",
        "execute.test.db.password",
    ).forEach { propertyName ->
        providers.gradleProperty(propertyName).orNull?.let { propertyValue ->
            systemProperty(propertyName, propertyValue)
        }
    }
}
kotlin {
    jvmToolchain(24)
}
