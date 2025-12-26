plugins {
    kotlin("jvm")
}

group = "gog.my_project"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":tools"))
    implementation(project(":data_base"))
    implementation(project(":annotations"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}