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

    api(project(":data_base:operation:query:ast"))
    implementation(project(":data_base:operation:query:builder"))
    api(project(":data_base:operation:query:api"))
    implementation(project(":data_base:operation:query:meta_data"))
    implementation(project(":data_base:operation:query:dialect"))
    implementation(project(":data_base:operation:query:renderer"))

    api(project(":data_base:manager:execute"))
    api(project(":data_base:manager:connection"))
    api(project(":data_base:operation:executor"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:operation:query:cte"))
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
