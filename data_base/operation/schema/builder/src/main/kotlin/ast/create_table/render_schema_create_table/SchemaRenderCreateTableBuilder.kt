package gog.my_project.data_base_schema.builder.ast.create_table.render_schema_create_table

import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.create_table.table.ISchemaTableApi
import gog.my_project.data_base_schema.api.interfaces.create_table.table_column.ISchemaColumnApi
import gog.my_project.data_base_schema.ast.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableAst
import gog.my_project.data_base_schema.ast.schema.create_table.column.SchemaColumnAst
import gog.my_project.data_base_schema.ast.schema.create_table.render_schema_create_table.SchemaRenderCreateTableAst
import gog.my_project.data_base_schema.ast.schema.create_table.table.SchemaTableAst
import gog.my_project.data_base_schema.builder.ast.create_table.column.SchemaColumnBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.definition.SchemaCreateTableForeignKeyBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.definition.SchemaCreateTableFullTextIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.definition.SchemaCreateTableIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.definition.SchemaCreateTablePrimaryKeyBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.definition.SchemaCreateTableUniqueBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.table.SchemaTableBuilder
import gog.my_project.data_base_schema.params.data_types.DateTimeType








class SchemaRenderCreateTableBuilder(
    override var ast: ISchemaRenderCreateTableAst = SchemaRenderCreateTableAst()
) : ISchemaRenderCreateTableApi {

    override fun table(
        blockTable: ISchemaTableApi.() -> Unit
    ): ISchemaRenderCreateTableApi {

        val ast = SchemaTableAst();
        SchemaTableBuilder(ast = ast).apply(blockTable);
        this.ast.schemaTableAst = ast;
        return this;

    }

    override fun addColumn(
        blockColumn: ISchemaColumnApi.() -> Unit
    ): ISchemaRenderCreateTableApi {

        val columnAst = SchemaColumnAst()
        SchemaColumnBuilder(ast = columnAst).apply(blockColumn)
        appendColumn(columnAst)
        return this

    }

    override fun timestamps(): ISchemaRenderCreateTableApi {
        appendTimestampColumns("created_at", "updated_at")
        return this
    }

    override fun softDeletes(): ISchemaRenderCreateTableApi {
        appendTimestampColumns("deleted_at")
        return this
    }

    private fun appendTimestampColumns(vararg names: String) {
        val normalizedNames = names.map(String::trim)
        normalizedNames.forEach { name ->
            appendColumnAst(SchemaColumnAst().apply {
                metadata.columnName = name
                metadata.columnDataType = DateTimeType()
                metadata.columnNullable = true
            })
        }
    }

    private fun appendColumn(columnAst: SchemaColumnAst) {
        appendColumnAst(columnAst)
    }

    private fun appendColumnAst(columnAst: SchemaColumnAst) {
        ast.columns.add(columnAst)
    }

    override fun primaryKey(
        block: gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTablePrimaryKeyApi.() -> Unit,
    ): ISchemaRenderCreateTableApi {
        val builder = SchemaCreateTablePrimaryKeyBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun unique(
        block: gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableUniqueApi.() -> Unit,
    ): ISchemaRenderCreateTableApi {
        val builder = SchemaCreateTableUniqueBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun index(
        block: gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableIndexApi.() -> Unit,
    ): ISchemaRenderCreateTableApi {
        val builder = SchemaCreateTableIndexBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun fullTextIndex(
        block: gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableFullTextIndexApi.() -> Unit,
    ): ISchemaRenderCreateTableApi {
        val builder = SchemaCreateTableFullTextIndexBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun foreignKey(
        block: gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableForeignKeyApi.() -> Unit,
    ): ISchemaRenderCreateTableApi {
        val builder = SchemaCreateTableForeignKeyBuilder().apply(block)
        ast.definitions.add(builder.build())
        return this
    }

    override fun ifNotExists(): ISchemaRenderCreateTableApi {
        ast.metadata.ifNotExists = true
        return this
    }

}
