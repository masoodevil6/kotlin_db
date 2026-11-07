package gog.my_project.data_base_schema

import gog.my_project.data_base_operation.config.data_base.DatabaseProduct
import gog.my_project.data_base_operation.config.data_base.MARIA_DB
import gog.my_project.data_base_operation.config.data_base.MYSQL
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.executor.integration.SchemaExecutionIntegration
import gog.my_project.data_base_schema.builder.ast.add_column.SchemaAddColumnBuilder
import gog.my_project.data_base_schema.builder.ast.create_foreign_key.SchemaCreateForeignKeyBuilder
import gog.my_project.data_base_schema.builder.ast.create_index.SchemaCreateIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.render_schema_create_table.SchemaRenderCreateTableBuilder
import gog.my_project.data_base_schema.builder.ast.create_unique_index.SchemaCreateUniqueIndexBuilder
import gog.my_project.data_base_schema.builder.ast.drop_column.SchemaDropColumnBuilder
import gog.my_project.data_base_schema.builder.ast.drop_foreign_key.SchemaDropForeignKeyBuilder
import gog.my_project.data_base_schema.builder.ast.drop_index.SchemaDropIndexBuilder
import gog.my_project.data_base_schema.builder.ast.drop_table.SchemaDropTableBuilder
import gog.my_project.data_base_schema.builder.ast.modify_column.SchemaModifyColumnBuilder
import gog.my_project.data_base_schema.builder.ast.rename_column.SchemaRenameColumnBuilder
import gog.my_project.data_base_schema.builder.ast.rename_table.SchemaRenameTableBuilder
import gog.my_project.data_base_schema.builder.ast.create_multi_column_index.SchemaCreateMultiColumnIndexBuilder as MultiColumnIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_full_text_index.SchemaCreateFullTextIndexBuilder as FullTextIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_spatial_index.SchemaCreateSpatialIndexBuilder as SpatialIndexBuilder
import gog.my_project.data_base_schema.renderer.manager.DialectSelector

/** Public entry point for constructing, rendering, and executing one Schema operation at a time. */
object Schema {
    private val dialectSelector = DialectSelector()

    fun createTable(block: SchemaCreateTableDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaRenderCreateTableBuilder()
        CreateTableDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun dropTable(block: SchemaDropTableDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaDropTableBuilder()
        DropTableDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun renameTable(block: SchemaRenameTableDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaRenameTableBuilder()
        RenameTableDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun addColumn(block: SchemaAddColumnDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaAddColumnBuilder()
        AddColumnDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun dropColumn(block: SchemaDropColumnDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaDropColumnBuilder()
        DropColumnDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun renameColumn(block: SchemaRenameColumnDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaRenameColumnBuilder()
        RenameColumnDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun modifyColumn(block: SchemaModifyColumnDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaModifyColumnBuilder()
        ModifyColumnDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun createIndex(block: SchemaCreateIndexDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaCreateIndexBuilder()
        CreateIndexDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun dropIndex(block: SchemaDropIndexDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaDropIndexBuilder()
        DropIndexDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun createUniqueIndex(block: SchemaCreateUniqueIndexDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaCreateUniqueIndexBuilder()
        CreateUniqueIndexDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun createMultiColumnIndex(block: SchemaCreateMultiColumnIndexDefinition.() -> Unit): SchemaOperation {
        val builder = MultiColumnIndexBuilder()
        CreateMultiColumnIndexDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun createFullTextIndex(block: SchemaCreateFullTextIndexDefinition.() -> Unit): SchemaOperation {
        val builder = FullTextIndexBuilder()
        CreateFullTextIndexDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun createSpatialIndex(block: SchemaCreateSpatialIndexDefinition.() -> Unit): SchemaOperation {
        val builder = SpatialIndexBuilder()
        CreateSpatialIndexDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun createForeignKey(block: SchemaCreateForeignKeyDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaCreateForeignKeyBuilder()
        CreateForeignKeyDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    fun dropForeignKey(block: SchemaDropForeignKeyDefinition.() -> Unit): SchemaOperation {
        val builder = SchemaDropForeignKeyBuilder()
        DropForeignKeyDefinitionAdapter(builder).block()
        return adaptSchemaOperation(builder)
    }

    /** Renders exactly one operation for an explicitly selected dialect and optional target version. */
    fun render(
        operation: SchemaOperation,
        dialect: SchemaDialect,
        version: SchemaVersion? = null,
    ): String {
        require(version == null || version.dialect == dialect) {
            "Schema version dialect ${version?.dialect} does not match selected dialect $dialect"
        }
        val coreVersion = version?.toCoreVersion()
        val rendered = dialectSelector.select(dialect.toCoreDialect(), coreVersion)
            .render(operation.requireSchemaApi().ast)
        return requireSchemaSql(rendered)
    }

    /**
     * Executes one operation on an Operation-owned, independent connection. This scope does not
     * start a transaction or change autoCommit; Connection acquisition and close remain Operation-owned.
     */
    fun execute(
        operation: SchemaOperation,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)? = null,
    ): ExecuteResult<Boolean> = execute(operation, DatabaseContext.legacyDefault(), onQueryInfo)

    /** Executes one operation using the selected database context. */
    fun execute(
        operation: SchemaOperation,
        databaseContext: DatabaseContext,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)? = null,
    ): ExecuteResult<Boolean> = OperationExecutionExecutor(databaseContext).connection { scope ->
        execute(operation, scope, onQueryInfo)
    }

    /** Executes one operation in an existing Operation scope without acquiring or closing a connection. */
    fun execute(
        operation: SchemaOperation,
        scope: OperationExecutionContext,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)? = null,
    ): ExecuteResult<Boolean> {
        val schemaApi = try {
            operation.requireSchemaApi()
        } catch (error: Error) {
            scope.failed<Boolean>(error)
            throw error
        } catch (exception: Exception) {
            return scope.failed(exception)
        }

        return SchemaExecutionIntegration.executeScoped(schemaApi, scope, onQueryInfo)
    }

}

private fun SchemaDialect.toCoreDialect(): DialectQuery = when (this) {
    SchemaDialect.MYSQL -> DialectQuery.MY_SQL
    SchemaDialect.MARIA_DB -> DialectQuery.MARIA_DB
}

internal fun requireSchemaSql(rendered: String?): String =
    requireNotNull(rendered) { "Schema renderer returned no SQL for the supplied operation" }

private fun DialectQuery.toSchemaDialect(): SchemaDialect = when (this) {
    DialectQuery.MY_SQL -> SchemaDialect.MYSQL
    DialectQuery.MARIA_DB -> SchemaDialect.MARIA_DB
}

private fun SchemaVersion.toCoreVersion() = when (dialect) {
    SchemaDialect.MYSQL -> MYSQL.Version(major, minor, patch)
    SchemaDialect.MARIA_DB -> MARIA_DB.Version(major, minor, patch)
}

private fun gog.my_project.data_base_operation.config.data_base.ProductDatabaseVersion.toSchemaVersion(
    dialect: SchemaDialect,
): SchemaVersion {
    val expectedProduct = when (dialect) {
        SchemaDialect.MYSQL -> DatabaseProduct.MYSQL
        SchemaDialect.MARIA_DB -> DatabaseProduct.MARIA_DB
    }
    require(product == expectedProduct) {
        "Configured database version product $product does not match dialect $dialect"
    }
    return SchemaVersion(dialect, major, minor, patch)
}
