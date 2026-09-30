package gog.my_project.data_base.migration.executor.interfaces

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi
import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.create_unique_index.IMigrationCreateUniqueIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi
import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi

/** Dispatches one API object to its existing operation-specific executor overload. */
fun IMigrationExecutor.execute(
    operation: IMigrationApi<*>,
    blockExecute: (ExecuteResult<Boolean>) -> Unit,
    blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
) {
    when (operation) {
        is IMigrationRenderCreateTableApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationDropTableApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationRenameTableApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationAddColumnApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationRenameColumnApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationDropColumnApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationModifyColumnApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationCreateIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationDropIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationCreateUniqueIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationCreateMultiColumnIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationCreateFullTextIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationCreateSpatialIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationCreateForeignKeyApi -> execute(operation, blockExecute, blockQueryInfo)
        is IMigrationDropForeignKeyApi -> execute(operation, blockExecute, blockQueryInfo)
        else -> throw IllegalArgumentException(
            "Unsupported migration operation API: ${operation::class.qualifiedName}",
        )
    }
}
