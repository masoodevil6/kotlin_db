plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:connection"))
    implementation(project(":data_base:annotations"))
    implementation(project(":data_base:models"))
    implementation(project(":data_base:query_builder"))

    // implementation(project(":dialect"))
//    implementation(project(":query"))

    implementation("mysql:mysql-connector-java:8.0.33")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}