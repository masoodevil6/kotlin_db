plugins {
    kotlin("jvm")
}

group = "gog.my_project"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation("mysql:mysql-connector-java:8.0.33")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}