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
include(":example")
include(":utils")

include(":tools")

// Collection package declarations use the logical root `gog.my_project.collection`;
// they are independent of these Gradle project paths and physical directories.
include(":collection:core")
include(":collection:examples")

include(":data_base")
include(":data_base:core")
include(":data_base:operation")
include(":data_base:operation:config")
include(":data_base:operation:executor")

include(":data_base:models")
include(":data_base:models:eloquent")


/// data_base
include(":data_base:manager")
include(":data_base:manager:connection")
include(":data_base:manager:execute")
include(":data_base:manager:example")


/// data_base -> operation -> query
include(":data_base:operation:query")
include(":data_base:operation:schema")
include(":data_base:operation:schema:params")
include(":data_base:operation:schema:ast")
include(":data_base:operation:schema:api")
include(":data_base:operation:schema:builder")
include(":data_base:operation:schema:dialect")
include(":data_base:operation:schema:renderer")
include(":data_base:operation:schema:executor")
include(":data_base:operation:schema:example")
include(":data_base:operation:query:ast")
include(":data_base:operation:query:api")
include(":data_base:operation:query:builder")
include(":data_base:operation:query:cte")
include(":data_base:operation:query:meta_data")
include(":data_base:operation:query:dialect")
include(":data_base:operation:query:renderer")
include(":data_base:operation:query:executor")
include(":data_base:operation:query:example")


/// data_base -> migration
include(":data_base:migration")
include(":data_base:migration:core")
include(":data_base:migration:api")
include(":data_base:migration:executor")
include(":data_base:migration:example")
