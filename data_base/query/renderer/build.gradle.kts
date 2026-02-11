plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.renderer"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))

    implementation(project(":data_base:query:ast"))
    implementation(project(":data_base:query:dialect"))


//    implementation(project(":data_base:connection"))
//
//    implementation(project(":data_base:query:builder"))
//    implementation(project(":data_base:query:dialect"))
//    implementation(project(":data_base:query:renderer"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}