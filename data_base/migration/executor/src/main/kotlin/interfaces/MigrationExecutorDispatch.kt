package gog.my_project.data_base.migration.executor.interfaces

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_foreign_key.ISchemaCreateForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_spatial_index.ISchemaCreateSpatialIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi
import gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi

/** Dispatches one API object to its existing operation-specific executor overload. */
fun IMigrationExecutor.execute(
    operation: ISchemaApi<*>,
    blockExecute: (ExecuteResult<Boolean>) -> Unit,
    blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
) {
    when (operation) {
        is ISchemaRenderCreateTableApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaDropTableApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaRenameTableApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaAddColumnApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaRenameColumnApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaDropColumnApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaModifyColumnApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaCreateIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaDropIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaCreateUniqueIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaCreateMultiColumnIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaCreateFullTextIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaCreateSpatialIndexApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaCreateForeignKeyApi -> execute(operation, blockExecute, blockQueryInfo)
        is ISchemaDropForeignKeyApi -> execute(operation, blockExecute, blockQueryInfo)
        else -> throw IllegalArgumentException(
            "Unsupported migration operation API: ${operation::class.qualifiedName}",
        )
    }
}
