plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

dependencies {
    // The executor module is the only direct project dependency in this fixture.
    implementation("gog.my_project.data_base.query.executor:executor:unspecified")
}

kotlin {
    jvmToolchain(24)
}
