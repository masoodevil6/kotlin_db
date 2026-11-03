package gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.api.interfaces.create_table.table.ISchemaTableApi
import gog.my_project.data_base_schema.api.interfaces.create_table.table_column.ISchemaColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTablePrimaryKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableUniqueApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableForeignKeyApi
import gog.my_project.data_base_schema.ast.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableAst

interface ISchemaRenderCreateTableApi: ISchemaApi<ISchemaRenderCreateTableAst> {

    fun table(blockTable: ISchemaTableApi.() -> Unit): ISchemaRenderCreateTableApi;

    fun addColumn(blockColumn: ISchemaColumnApi.() -> Unit): ISchemaRenderCreateTableApi;

    fun timestamps(): ISchemaRenderCreateTableApi;

    fun softDeletes(): ISchemaRenderCreateTableApi;

    fun primaryKey(block: ISchemaCreateTablePrimaryKeyApi.() -> Unit): ISchemaRenderCreateTableApi;

    fun unique(block: ISchemaCreateTableUniqueApi.() -> Unit): ISchemaRenderCreateTableApi;

    fun index(block: ISchemaCreateTableIndexApi.() -> Unit): ISchemaRenderCreateTableApi;

    fun fullTextIndex(block: ISchemaCreateTableFullTextIndexApi.() -> Unit): ISchemaRenderCreateTableApi;

    fun foreignKey(block: ISchemaCreateTableForeignKeyApi.() -> Unit): ISchemaRenderCreateTableApi;

    fun ifNotExists(): ISchemaRenderCreateTableApi;

}
