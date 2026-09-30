package gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.api.interfaces.create_table.table.IMigrationTableApi
import gog.my_project.data_base.migration.api.interfaces.create_table.table_column.IMigrationColumnApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTablePrimaryKeyApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableUniqueApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableFullTextIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableForeignKeyApi
import gog.my_project.data_base.migration.ast.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableAst

interface IMigrationRenderCreateTableApi: IMigrationApi<IMigrationRenderCreateTableAst> {

    fun table(blockTable: IMigrationTableApi.() -> Unit): IMigrationRenderCreateTableApi;

    fun addColumn(blockColumn: IMigrationColumnApi.() -> Unit): IMigrationRenderCreateTableApi;

    fun timestamps(): IMigrationRenderCreateTableApi;

    fun softDeletes(): IMigrationRenderCreateTableApi;

    fun primaryKey(block: IMigrationCreateTablePrimaryKeyApi.() -> Unit): IMigrationRenderCreateTableApi;

    fun unique(block: IMigrationCreateTableUniqueApi.() -> Unit): IMigrationRenderCreateTableApi;

    fun index(block: IMigrationCreateTableIndexApi.() -> Unit): IMigrationRenderCreateTableApi;

    fun fullTextIndex(block: IMigrationCreateTableFullTextIndexApi.() -> Unit): IMigrationRenderCreateTableApi;

    fun foreignKey(block: IMigrationCreateTableForeignKeyApi.() -> Unit): IMigrationRenderCreateTableApi;

    fun ifNotExists(): IMigrationRenderCreateTableApi;

}
