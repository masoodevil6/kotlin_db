package gog.my_project.data_base_migration.executor.service.version_selection

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelection
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelectionDirection
import model.prepared.PreparedMigrationVersion
import service.version.MigrationVersionPreparationService

/** Selects registered Versions using Configuration order and prepares them without executing them. */
internal class MigrationVersionSelectionService(
    private val configuration: MigrationConfiguration,
    private val preparationService: MigrationVersionPreparationService = MigrationVersionPreparationService(),
) {
    fun select(
        current: PreparedMigrationVersion?,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
    ): ExecuteResult<MigrationVersionSelection> = try {
        val currentIndex = current?.let(::indexOfCurrent)
        val targetIndex = target?.let(::indexOfTarget)
        val selected = when (direction) {
            MigrationVersionSelectionDirection.UP -> selectUp(currentIndex, targetIndex)
            MigrationVersionSelectionDirection.DOWN -> selectDown(currentIndex, targetIndex)
        }

        ExecuteResult.Success(
            MigrationVersionSelection(
                direction = direction,
                versions = selected.map(preparationService::prepare),
            ),
        )
    } catch (error: Error) {
        throw error
    } catch (failure: Throwable) {
        ExecuteResult.Failure(failure)
    }

    private fun indexOfCurrent(current: PreparedMigrationVersion): Int =
        configuration.registrations.indexOfFirst { it.name == current.version.name }
            .takeIf { it >= 0 }
            ?: throw IllegalArgumentException(
                "Current Migration Version '${current.version.name}' is not registered in MigrationConfiguration",
            )

    private fun indexOfTarget(target: MigrationVersionTag): Int {
        val targetName = target.validatedValue
        return configuration.registrations.indexOfFirst { it.name == targetName }
            .takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Migration Version '$targetName' is not registered")
    }

    private fun selectUp(currentIndex: Int?, targetIndex: Int?): List<MigrationVersion> {
        if (targetIndex != null && currentIndex != null && targetIndex <= currentIndex) {
            val targetName = configuration.registrations[targetIndex].name
            throw IllegalStateException("Migration Version '$targetName' has already been applied")
        }

        val startIndex = currentIndex?.plus(1) ?: 0
        val endExclusive = targetIndex?.plus(1) ?: configuration.registrations.size
        if (startIndex >= endExclusive) return emptyList()
        return configuration.registrations.subList(startIndex, endExclusive)
    }

    private fun selectDown(currentIndex: Int?, targetIndex: Int?): List<MigrationVersion> {
        if (currentIndex == null) {
            if (targetIndex != null) {
                val targetName = configuration.registrations[targetIndex].name
                throw IllegalStateException(
                    "Migration Version '$targetName' cannot be rolled back because no Version is applied",
                )
            }
            return emptyList()
        }

        if (targetIndex != null && targetIndex > currentIndex) {
            val targetName = configuration.registrations[targetIndex].name
            throw IllegalStateException(
                "Migration Version '$targetName' is later than the current applied Version " +
                    "'${configuration.registrations[currentIndex].name}'",
            )
        }

        if (targetIndex == null) return listOf(configuration.registrations[currentIndex])
        val startIndex = targetIndex + 1
        if (startIndex > currentIndex) return emptyList()
        return configuration.registrations.subList(startIndex, currentIndex + 1).asReversed()
    }
}
