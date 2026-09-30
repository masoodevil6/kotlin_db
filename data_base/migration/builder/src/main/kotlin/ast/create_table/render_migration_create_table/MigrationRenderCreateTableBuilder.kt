package gog.my_project.data_base.migration.builder.ast.create_table.render_migration_create_table

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.api.interfaces.create_table.table.IMigrationTableApi
import gog.my_project.data_base.migration.api.interfaces.create_table.table_column.IMigrationColumnApi
import gog.my_project.data_base.migration.ast.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableAst
import gog.my_project.data_base.migration.ast.schema.create_table.column.MigrationColumnAst
import gog.my_project.data_base.migration.ast.schema.create_table.render_migration_create_table.MigrationRenderCreateTableAst
import gog.my_project.data_base.migration.ast.schema.create_table.table.MigrationTableAst
import gog.my_project.data_base.migration.builder.ast.create_table.definition.MigrationCreateTablePrimaryKeyBuilder
import gog.my_project.data_base.migration.builder.ast.create_table.definition.MigrationCreateTableUniqueBuilder
import gog.my_project.data_base.migration.builder.ast.create_table.definition.MigrationCreateTableIndexBuilder
import gog.my_project.data_base.migration.builder.ast.create_table.definition.MigrationCreateTableFullTextIndexBuilder
import gog.my_project.data_base.migration.builder.ast.create_table.definition.MigrationCreateTableForeignKeyBuilder
import gog.my_project.data_base.migration.builder.ast.create_table.column.MigrationColumnBuilder
import gog.my_project.data_base.migration.builder.ast.create_table.table.MigrationTableBuilder
import gog.my_project.data_base.migration.params.data_types.DateTimeType

class MigrationRenderCreateTableBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IMigrationRenderCreateTableAst = MigrationRenderCreateTableAst()
) : IMigrationRenderCreateTableApi {

    override fun table(
        blockTable: IMigrationTableApi.() -> Unit
    ): IMigrationRenderCreateTableApi {

        val ast = MigrationTableAst();
        MigrationTableBuilder(
            params , ast
        ).apply(blockTable);
        this.ast.migrationTableAst = ast;
        return this;

    }

    override fun addColumn(
        blockColumn: IMigrationColumnApi.() -> Unit
    ): IMigrationRenderCreateTableApi {

        val columnAst = MigrationColumnAst()
        MigrationColumnBuilder(params, columnAst).apply(blockColumn)
        appendColumn(columnAst)
        return this

    }

    override fun timestamps(): IMigrationRenderCreateTableApi {
        appendTimestampColumns("created_at", "updated_at")
        return this
    }

    override fun softDeletes(): IMigrationRenderCreateTableApi {
        appendTimestampColumns("deleted_at")
        return this
    }

    private fun appendTimestampColumns(vararg names: String) {
        val normalizedNames = names.map(String::trim)
        require(normalizedNames.distinctBy { it.lowercase() }.size == normalizedNames.size) {
            "CREATE TABLE shorthand generated duplicate column names"
        }
        normalizedNames.forEach { name -> validateColumnNameAvailable(name) }
        normalizedNames.forEach { name ->
            appendColumnAst(MigrationColumnAst().apply {
                columnName = name
                columnDataType = DateTimeType()
                columnNullable = true
            })
        }
    }

    private fun appendColumn(columnAst: MigrationColumnAst) {
        val name = requireNotNull(columnAst.columnName?.takeIf(String::isNotBlank)) {
            "CREATE TABLE column name must not be blank"
        }
        validateColumnNameAvailable(name)
        appendColumnAst(columnAst)
    }

    private fun validateColumnNameAvailable(name: String) {
        val duplicate = ast.columns.any { existing ->
            existing.columnName?.trim()?.equals(name.trim(), ignoreCase = true) == true
        }
        require(!duplicate) { "CREATE TABLE has duplicate column name '$name'" }
    }

    private fun appendColumnAst(columnAst: MigrationColumnAst) {
        ast.columns.add(columnAst)
    }

    override fun primaryKey(
        block: gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTablePrimaryKeyApi.() -> Unit,
    ): IMigrationRenderCreateTableApi {
        val builder = MigrationCreateTablePrimaryKeyBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun unique(
        block: gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableUniqueApi.() -> Unit,
    ): IMigrationRenderCreateTableApi {
        val builder = MigrationCreateTableUniqueBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun index(
        block: gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableIndexApi.() -> Unit,
    ): IMigrationRenderCreateTableApi {
        val builder = MigrationCreateTableIndexBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun fullTextIndex(
        block: gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableFullTextIndexApi.() -> Unit,
    ): IMigrationRenderCreateTableApi {
        val builder = MigrationCreateTableFullTextIndexBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun foreignKey(
        block: gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableForeignKeyApi.() -> Unit,
    ): IMigrationRenderCreateTableApi {
        val builder = MigrationCreateTableForeignKeyBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun ifNotExists(): IMigrationRenderCreateTableApi {
        ast.ifNotExists = true
        return this
    }

}
