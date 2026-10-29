pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "executor-api-consumer"

includeBuild("D:/projects/kotlin/query_builder/master/my_project") {
    dependencySubstitution {
        substitute(module("gog.my_project.data_base.query.executor:executor"))
            .using(project(":data_base:query:executor"))
    }
}
