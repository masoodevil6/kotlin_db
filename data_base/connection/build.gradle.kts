plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.connection"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("mysql:mysql-connector-java:8.0.33")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}