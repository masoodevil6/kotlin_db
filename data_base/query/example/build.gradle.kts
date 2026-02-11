plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query.example"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":tools"))

    implementation(project(":data_base:core"))
    implementation(project(":data_base:models"))
    implementation(project(":data_base:cte"))

    implementation(project(":data_base:query:api"))
    implementation(project(":data_base:query:renderer"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}