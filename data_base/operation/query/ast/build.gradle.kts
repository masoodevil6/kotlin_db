plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.ast"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))

    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.20")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}