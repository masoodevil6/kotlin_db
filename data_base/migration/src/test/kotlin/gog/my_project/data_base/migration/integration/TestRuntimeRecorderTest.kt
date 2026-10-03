package gog.my_project.data_base.migration.integration

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class TestRuntimeRecorderTest {
    private val temporaryDirectories = mutableListOf<Path>()

    @AfterTest
    fun removeTemporaryDirectories() {
        temporaryDirectories.asReversed().forEach { root ->
            if (Files.exists(root)) {
                Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            }
        }
    }

    private fun temporaryDirectory(prefix: String): Path = Files.createTempDirectory(prefix).also(temporaryDirectories::add)

    @Test
    fun receivesTheInternalHelpRuntimePropertiesAsACompletePair() {
        val runId = System.getProperty(TestRuntimeRecorder.RUN_ID_PROPERTY)
        val path = System.getProperty(TestRuntimeRecorder.PATH_PROPERTY)
        assertEquals(runId == null, path == null, "Help must pass both internal Runtime properties or neither")
        if (runId != null) {
            assertEquals(runId, UUID.fromString(runId).toString())
            assertTrue(Path.of(requireNotNull(path)).isAbsolute)
        }
    }

    @Test
    fun publishesCompleteCheckpointsAtomicallyToTheRunSpecificPath() {
        val buildRoot = temporaryDirectory("help-runtime-build-")
        Files.writeString(buildRoot.resolve("settings.gradle.kts"), "")
        val projectRoot = Files.createDirectories(buildRoot.resolve("data_base/migration"))
        val runId = UUID.randomUUID().toString()
        val runtimeDirectory = Files.createDirectories(projectRoot.resolve("build/reports/help/runtime/$runId"))
        val artifact = runtimeDirectory.resolve("runtime.json")
        val recorder = assertNotNull(
            TestRuntimeRecorder.create(
                TestRuntimeRecorder.PROJECT_PATH,
                TestRuntimeRecorder.SYSTEM_TEST_ID,
                listOf("prepare-config", "final-state"),
                suppliedRunId = runId,
                suppliedPath = artifact.toString(),
                buildRootOverride = buildRoot,
            ),
        )

        recorder.begin()
        assertFalse(Files.exists(artifact), "No Runtime is published before the first workflow checkpoint")
        recorder.startStep("prepare-config")
        assertTrue(Files.readString(artifact).contains("\"runtimeStatus\":\"incomplete\""))
        recorder.succeedStep("prepare-config")
        recorder.startStep("final-state")
        recorder.succeedStep("final-state")
        recorder.complete()

        val json = Files.readString(artifact)
        assertTrue(json.contains("\"runId\":\"$runId\""))
        assertTrue(json.contains("\"runtimeStatus\":\"complete\""))
        assertEquals(1, Files.list(runtimeDirectory).use { paths -> paths.count() })
        assertFalse(json.contains("password", ignoreCase = true))
    }

    @Test
    fun rejectsRuntimePathsOutsideTheCurrentProjectOrWithAnotherRunId() {
        val buildRoot = temporaryDirectory("help-runtime-build-")
        Files.writeString(buildRoot.resolve("settings.gradle.kts"), "")
        Files.createDirectories(buildRoot.resolve("data_base/migration"))
        val runId = UUID.randomUUID().toString()
        val outside = temporaryDirectory("help-runtime-outside-").resolve("build/reports/help/runtime/$runId/runtime.json")
        assertNull(
            TestRuntimeRecorder.create(
                TestRuntimeRecorder.PROJECT_PATH,
                TestRuntimeRecorder.SYSTEM_TEST_ID,
                listOf("prepare-config"),
                suppliedRunId = runId,
                suppliedPath = outside.toString(),
                buildRootOverride = buildRoot,
            ),
        )
        val wrongRunPath = Path.of(buildRoot.toString(), "data_base/migration/build/reports/help/runtime", UUID.randomUUID().toString(), "runtime.json")
        assertNull(
            TestRuntimeRecorder.create(
                TestRuntimeRecorder.PROJECT_PATH,
                TestRuntimeRecorder.SYSTEM_TEST_ID,
                listOf("prepare-config"),
                suppliedRunId = runId,
                suppliedPath = wrongRunPath.toString(),
                buildRootOverride = buildRoot,
            ),
        )
    }
}
