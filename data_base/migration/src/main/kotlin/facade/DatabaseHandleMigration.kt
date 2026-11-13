package gog.my_project.data_base_migration.facade

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleAttributes
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration

/**
 * Runs the Migration receiver block with the configuration attached to this handle's Registration
 * and in this handle's Manager-owned Operation scope.
 *
 * Handles that do not expose Registration attributes or an Operation scope fail before invoking
 * [block].
 */
fun <T> DatabaseHandleApi.migration(
    block: MigrationFacade.() -> T,
): ExecuteResult<T> {
    val attributes = this as? DatabaseHandleAttributes
        ?: return ExecuteResult.Failure(
            UnsupportedOperationException(
                "This DatabaseHandleApi implementation does not expose Registration attributes",
            ),
        )

    val configuration = try {
        attributes.attribute(MigrationConfigurationRegistration.key)
    } catch (error: Error) {
        throw error
    } catch (exception: Exception) {
        return ExecuteResult.Failure(exception)
    } ?: return ExecuteResult.Failure(
        IllegalStateException("No MigrationConfiguration is attached to this database Registration"),
    )

    return migration(configuration, block)
}

/**
 * Runs the Migration receiver block in this handle's Manager-owned Operation scope.
 * The block's value is wrapped in the outer Operation result; a returned [ExecuteResult] remains
 * a nested value and is not flattened.
 */
fun <T> DatabaseHandleApi.migration(
    configuration: MigrationConfiguration,
    block: MigrationFacade.() -> T,
): ExecuteResult<T> = withOperationScope { scope ->
    ExecuteResult.Success(migrationFacade(configuration, scope, block))
}
