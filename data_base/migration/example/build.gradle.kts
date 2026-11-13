import java.io.File
import java.io.Serializable
import org.gradle.api.Action
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts

plugins {
    kotlin("jvm")
    application
}

group = "gog.my_project.data_base.migration.example"
version = "unspecified"

class PrefixProjectJarNames(private val projectRootPath: String) : Action<FileCopyDetails>, Serializable {
    override fun execute(details: FileCopyDetails) {
        val root = File(projectRootPath).toPath().toAbsolutePath().normalize()
        val source = details.file.toPath().toAbsolutePath().normalize()
        if (!source.startsWith(root)) return

        val relativePath = root.relativize(source).toString().replace('\\', '/')
        val buildLibMarker = "/build/libs/"
        val markerIndex = relativePath.lastIndexOf(buildLibMarker)
        if (markerIndex <= 0) return

        val modulePath = relativePath.substring(0, markerIndex)
            .replace('/', '-')
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
        details.name = "$modulePath-${details.name}"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":data_base:migration:api"))
    testImplementation(kotlin("test"))
    testImplementation(project(":data_base:operation:schema:ast"))
    testImplementation(project(":data_base:operation:schema:builder"))
    testImplementation(project(":data_base:operation:schema:params"))
    testImplementation(project(":data_base:operation:schema:renderer"))

    implementation(project(":data_base:operation:schema"))
}

application {
    mainClass.set("gog.my_project.data_base.migration.example.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

// Project modules can produce JARs with the same filename (for example,
// several modules produce executor.jar). Keep their published artifact names
// unchanged, but give each JAR a unique name in this application's distribution.
val distributionClasspathDirectory = layout.buildDirectory.dir("distribution/classpath")
val prepareDistributionClasspath = tasks.register<Sync>("prepareDistributionClasspath") {
    from(tasks.named("jar"))
    from(configurations.runtimeClasspath)
    into(distributionClasspathDirectory)
    eachFile(PrefixProjectJarNames(rootProject.projectDir.absolutePath))
    include("**/*.jar")
}

tasks.named<CreateStartScripts>("startScripts") {
    dependsOn(prepareDistributionClasspath)
    classpath = files(fileTree(distributionClasspathDirectory))
}

distributions {
    main {
        contents {
            // Keep distribution JAR names aligned with the staged start-script classpath.
            eachFile(PrefixProjectJarNames(rootProject.projectDir.absolutePath))
        }
    }
}

kotlin {
    jvmToolchain(24)
}
