plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.manager.execute"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:manager:connection"))
    implementation(project(":data_base:core"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    listOf(
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
