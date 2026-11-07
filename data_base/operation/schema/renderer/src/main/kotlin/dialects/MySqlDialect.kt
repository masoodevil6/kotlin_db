package gog.my_project.data_base_schema.renderer.dialects

import gog.my_project.data_base_operation.config.data_base.ProductDatabaseVersion
import gog.my_project.data_base_operation.config.data_base.DatabaseProduct
import gog.my_project.data_base_operation.config.data_base.MARIA_DB
import gog.my_project.data_base_operation.config.data_base.MYSQL
import gog.my_project.data_base_schema.ast.interfaces.add_column.ISchemaAddColumnAst
import gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.ISchemaCreateTableDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.table.ISchemaTableAst
import gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst
import gog.my_project.data_base_schema.ast.interfaces.rename_table.ISchemaRenameTableAst
import gog.my_project.data_base_schema.ast.interfaces.rename_column.ISchemaRenameColumnAst
import gog.my_project.data_base_schema.ast.interfaces.modify_column.ISchemaModifyColumnAst
import gog.my_project.data_base_schema.ast.interfaces.create_index.ISchemaCreateIndexAst
import gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst
import gog.my_project.data_base_schema.ast.interfaces.create_unique_index.ISchemaCreateUniqueIndexAst
import gog.my_project.data_base_schema.ast.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexAst
import gog.my_project.data_base_schema.ast.interfaces.create_full_text_index.ISchemaCreateFullTextIndexAst
import gog.my_project.data_base_schema.ast.interfaces.create_spatial_index.ISchemaCreateSpatialIndexAst
import gog.my_project.data_base_schema.ast.interfaces.create_foreign_key.ISchemaCreateForeignKeyAst
import gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst
import gog.my_project.data_base_schema.ast.interfaces.if_exist.ISchemaIfExistAst
import gog.my_project.data_base_schema.dialect.manager.BaseSqlDialect
import gog.my_project.data_base_schema.renderer.nodes.create_table.column.MySqlSchemaColumnCapability
import gog.my_project.data_base_schema.renderer.nodes.create_table.definition.MySqlSchemaCreateTableDefinitionCapability
import gog.my_project.data_base_schema.renderer.nodes.add_column.MySqlSchemaAddColumnCapability
import gog.my_project.data_base_schema.renderer.nodes.drop_column.MySqlSchemaDropColumnCapability
import gog.my_project.data_base_schema.renderer.nodes.create_table.render_schema_create_table.MySqlSchemaRenderCreateTableCapability
import gog.my_project.data_base_schema.renderer.nodes.create_table.table.MySqlSchemaTableCapability
import gog.my_project.data_base_schema.renderer.nodes.drop_table.MySqlSchemaDropTableCapability
import gog.my_project.data_base_schema.renderer.nodes.rename_table.MySqlSchemaRenameTableCapability
import gog.my_project.data_base_schema.renderer.nodes.rename_column.MySqlSchemaRenameColumnCapability
import gog.my_project.data_base_schema.renderer.nodes.modify_column.MySqlSchemaModifyColumnCapability
import gog.my_project.data_base_schema.renderer.nodes.create_index.MySqlSchemaCreateIndexCapability
import gog.my_project.data_base_schema.renderer.nodes.drop_index.MySqlSchemaDropIndexCapability
import gog.my_project.data_base_schema.renderer.nodes.create_unique_index.MySqlSchemaCreateUniqueIndexCapability
import gog.my_project.data_base_schema.renderer.nodes.create_multi_column_index.MySqlSchemaCreateMultiColumnIndexCapability
import gog.my_project.data_base_schema.renderer.nodes.create_full_text_index.MySqlSchemaCreateFullTextIndexCapability
import gog.my_project.data_base_schema.renderer.nodes.create_spatial_index.MySqlSchemaCreateSpatialIndexCapability
import gog.my_project.data_base_schema.renderer.nodes.create_foreign_key.MySqlSchemaCreateForeignKeyCapability
import gog.my_project.data_base_schema.renderer.nodes.drop_foreign_key.MySqlSchemaDropForeignKeyCapability
import gog.my_project.data_base_schema.renderer.nodes.if_exist.MySqlSchemaIfExistCapability
import gog.my_project.data_base_schema.dialect.nodes.rename_column.IRenameColumnSqlDialect
import gog.my_project.data_base_schema.dialect.nodes.rename_column.RenameColumnSqlStrategy

open class MySqlDialect(
    private val targetVersion: ProductDatabaseVersion? = null,
    private val dialectProduct: DatabaseProduct = DatabaseProduct.MYSQL,
) : BaseSqlDialect(), IRenameColumnSqlDialect {

    override val renameColumnSqlStrategy: RenameColumnSqlStrategy
        get() {
            val version = targetVersion ?: return RenameColumnSqlStrategy.NATIVE_RENAME
            require(version.product == dialectProduct) {
                "Target version product ${version.product} does not match dialect $dialectProduct"
            }
            val nativeSupported = when (dialectProduct) {
                DatabaseProduct.MYSQL -> version >= MYSQL.V8_0
                DatabaseProduct.MARIA_DB -> version >= MARIA_DB.V10_5_3
            }
            return if (nativeSupported) RenameColumnSqlStrategy.NATIVE_RENAME
            else RenameColumnSqlStrategy.CHANGE_COLUMN
        }

    override fun renderLegacyRenameColumn(
        ast: ISchemaRenameColumnAst,
        definitionSuffix: String,
    ): String? {
        check(renameColumnSqlStrategy == RenameColumnSqlStrategy.CHANGE_COLUMN) {
            "Configured target selects native RENAME COLUMN, not CHANGE COLUMN"
        }
        return MySqlSchemaRenameColumnCapability().renderLegacy(ast, definitionSuffix)
    }

    override val _prefixCreateTable: String = "CREATE TABLE"
    override fun registerRenders() {

        /// create table
        _registry.register(ISchemaRenderCreateTableAst::class       , MySqlSchemaRenderCreateTableCapability());
        _registry.register(ISchemaTableAst::class                   , MySqlSchemaTableCapability());
        _registry.register(ISchemaColumnAst::class                  , MySqlSchemaColumnCapability());
        _registry.register(ISchemaCreateTableDefinitionAst::class, MySqlSchemaCreateTableDefinitionCapability());

        /// drop table
        _registry.register(ISchemaDropTableAst::class                , MySqlSchemaDropTableCapability());
        _registry.register(ISchemaIfExistAst::class, MySqlSchemaIfExistCapability());

        /// rename table
        _registry.register(ISchemaRenameTableAst::class               , MySqlSchemaRenameTableCapability());
        _registry.register(ISchemaRenameColumnAst::class              , MySqlSchemaRenameColumnCapability());

        /// add column
        _registry.register(ISchemaAddColumnAst::class                  , MySqlSchemaAddColumnCapability());

        /// drop column
        _registry.register(
            ISchemaDropColumnAst::class,
            MySqlSchemaDropColumnCapability(),
        );

        /// modify column
        _registry.register(ISchemaModifyColumnAst::class, MySqlSchemaModifyColumnCapability());

        /// create index
        _registry.register(ISchemaCreateIndexAst::class, MySqlSchemaCreateIndexCapability());

        /// drop index
        _registry.register(ISchemaDropIndexAst::class, MySqlSchemaDropIndexCapability());

        /// create unique index
        _registry.register(ISchemaCreateUniqueIndexAst::class, MySqlSchemaCreateUniqueIndexCapability());

        /// create multi-column index
        _registry.register(ISchemaCreateMultiColumnIndexAst::class, MySqlSchemaCreateMultiColumnIndexCapability());

        /// create full-text index
        _registry.register(ISchemaCreateFullTextIndexAst::class, MySqlSchemaCreateFullTextIndexCapability());

        /// create spatial index
        _registry.register(ISchemaCreateSpatialIndexAst::class, MySqlSchemaCreateSpatialIndexCapability());

        /// foreign keys
        _registry.register(ISchemaCreateForeignKeyAst::class, MySqlSchemaCreateForeignKeyCapability());
        _registry.register(ISchemaDropForeignKeyAst::class, MySqlSchemaDropForeignKeyCapability());

    }

}
