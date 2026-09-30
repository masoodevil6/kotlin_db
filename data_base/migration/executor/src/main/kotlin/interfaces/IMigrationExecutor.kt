package gog.my_project.data_base.migration.executor.interfaces

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi
import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_unique_index.IMigrationCreateUniqueIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi
import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.renderer.manager.DialectSelector
import gog.my_project.tools.scripts.StringTools


interface IMigrationExecutor {

    val queryExecutor: IQueryExecute
    val dialectSelector: DialectSelector

    //// [EXECUTE]: create table | alter table | drop table
    fun execute(
        queryBuilder: IMigrationRenderCreateTableApi,
        blockExecute:    (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationDropTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationRenameTableApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationAddColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationRenameColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationDropColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationModifyColumnApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationCreateIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationDropIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationCreateUniqueIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationCreateMultiColumnIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationCreateFullTextIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationCreateSpatialIndexApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null
    )

    fun execute(
        queryBuilder: IMigrationCreateForeignKeyApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    )

    fun execute(
        queryBuilder: IMigrationDropForeignKeyApi,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    )



    //// [TOOLS]
    fun <Ast: IMigrationAst,Api : IMigrationApi<Ast>> executeOut(
        migrationBuilder:    Api,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null,
        blockResult:     (db: IQueryExecute ,builtQuery: BuiltQuery) -> Unit
    ){
        val db = queryExecutor

        val dialect  = dialectSelector.select(DefaultDatabaseConfig.config.dialect)

        val query = dialect.render(migrationBuilder.ast) ?: throw IllegalStateException("rendered sql is null")

        val params: MutableList<SqlParameter<*>> = migrationBuilder.params;

        if (blockQueryInfo != null){
            val paramsMap: MutableMap<String , Any?> = params.associate { it -> it.name to it.value }.toMutableMap()
            val queryString = StringTools.formatSql(query)

            blockQueryInfo(queryString , paramsMap)
        }

        blockResult(db , BuiltQuery(query, params));
    }

}
