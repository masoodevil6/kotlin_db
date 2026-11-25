plugins {
    kotlin("jvm")
}

group = "gog.my_project.data_base_session.example"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:operation:session:builder"))
    implementation(project(":data_base:operation:session:executor"))

    runtimeOnly("mysql:mysql-connector-java:8.0.33")
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the Session advisory lock examples against MySQL."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("gog.my_project.data_base_session.example.MainKt")
}

kotlin {
    jvmToolchain(24)
}
