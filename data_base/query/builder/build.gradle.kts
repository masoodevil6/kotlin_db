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

    implementation(project(":data_base:models:eloquent"))

    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.20")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}