plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query_builder.examples"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:query_builder:renderer"))
    implementation(project(":data_base:query_builder:query"))
    //implementation(project(":data_base"))
   // implementation(project(":dialect"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}