plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query_builder.dialect"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:connection"))
    implementation(project(":data_base:annotations"))
    implementation(project(":data_base:query_builder:query"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}