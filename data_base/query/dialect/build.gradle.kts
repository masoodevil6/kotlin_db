plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.dialect"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))
    implementation(project(":data_base:query:ast"))
    implementation(project(":data_base:query:api"))
    implementation(project(":data_base:query:builder"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}