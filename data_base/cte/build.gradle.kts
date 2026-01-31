plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.cte"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:core"))
    implementation(project(":data_base:models"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}