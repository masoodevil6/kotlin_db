plugins {
    kotlin("jvm")
    id("java-library")
}

group = "gog.my_project.data_base.operation.schema.executor"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":data_base:operation:executor"))
    api(project(":data_base:operation:schema:api"))
    api(project(":data_base:operation:schema:renderer"))
    api(project(":data_base:manager:execute"))
    api(project(":data_base:manager:connection"))
    api(project(":data_base:core"))
    implementation(project(":data_base:operation:config"))
    implementation(project(":tools"))

    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:operation:schema:builder"))
}

tasks.test {
    useJUnitPlatform()
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
