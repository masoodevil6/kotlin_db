// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// Include the `app` and `utils` subprojects in the build.
// If there are changes in only one of the projects, Gradle will rebuild only the one that has changed.
// Learn more about structuring projects with Gradle - https://docs.gradle.org/8.7/userguide/multi_project_builds.html

rootProject.name = "my_project"

include(":app")
include(":utils")

include(":tools")


include(":data_base")
include(":data_base:core")
include(":data_base:models")
include(":data_base:cte")

include(":data_base:manager")
include(":data_base:manager:connection")
include(":data_base:manager:execute")

include(":data_base:query")
include(":data_base:query:ast")
include(":data_base:query:api")
include(":data_base:query:builder")
include(":data_base:query:dialect")
include(":data_base:query:renderer")
include(":data_base:query:executor")
include(":data_base:query:example")