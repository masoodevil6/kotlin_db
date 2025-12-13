plugins {
    id("java")
    kotlin("jvm")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.20")

    implementation("mysql:mysql-connector-java:8.0.33")
    implementation(kotlin("stdlib-jdk8"))
}


kotlin {
    jvmToolchain(20)
}


tasks.test {
    useJUnitPlatform()
}

