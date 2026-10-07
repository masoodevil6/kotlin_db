plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.builder"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))
    implementation(project(":data_base:query:ast"))
    implementation(project(":data_base:query:api"))

    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.20")

    testImplementation(project(":data_base:query:dialect"))
    testImplementation(project(":data_base:models:eloquent"))
    testImplementation(project(":data_base:query:renderer"))
    testImplementation(project(":data_base:manager:execute"))
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
