plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base.query_builder.query"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:connection"))
    implementation(project(":data_base:annotations"))
    implementation(project(":data_base:models"))

    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.20")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}