package gog.my_project.data_base.migration.renderer.dialects

import gog.my_project.data_base.core.data_base.ProductDatabaseVersion
import gog.my_project.data_base.core.data_base.DatabaseProduct
import gog.my_project.data_base.core.data_base.MARIA_DB
import gog.my_project.data_base.core.data_base.MYSQL
import gog.my_project.data_base.migration.ast.interfaces.add_column.IMigrationAddColumnAst
import gog.my_project.data_base.migration.ast.interfaces.drop_column.IMigrationDropColumnAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.IMigrationCreateTableDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.table.IMigrationTableAst
import gog.my_project.data_base.migration.ast.interfaces.drop_table.IMigrationDropTableAst
import gog.my_project.data_base.migration.ast.interfaces.rename_table.IMigrationRenameTableAst
import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst
import gog.my_project.data_base.migration.ast.interfaces.modify_column.IMigrationModifyColumnAst
import gog.my_project.data_base.migration.ast.interfaces.create_index.IMigrationCreateIndexAst
import gog.my_project.data_base.migration.ast.interfaces.drop_index.IMigrationDropIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_unique_index.IMigrationCreateUniqueIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_full_text_index.IMigrationCreateFullTextIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_spatial_index.IMigrationCreateSpatialIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_foreign_key.IMigrationCreateForeignKeyAst
import gog.my_project.data_base.migration.ast.interfaces.drop_foreign_key.IMigrationDropForeignKeyAst
import gog.my_project.data_base.migration.dialect.manager.BaseSqlDialect
import gog.my_project.data_base.migration.renderer.nodes.create_table.column.MySqlMigrationColumnCapability
import gog.my_project.data_base.migration.renderer.nodes.create_table.definition.MySqlMigrationCreateTableDefinitionCapability
import gog.my_project.data_base.migration.renderer.nodes.add_column.MySqlMigrationAddColumnCapability
import gog.my_project.data_base.migration.renderer.nodes.drop_column.MySqlMigrationDropColumnCapability
import gog.my_project.data_base.migration.renderer.nodes.create_table.render_migration_create_table.MySqlMigrationRenderCreateTableCapability
import gog.my_project.data_base.migration.renderer.nodes.create_table.table.MySqlMigrationTableCapability
import gog.my_project.data_base.migration.renderer.nodes.drop_table.MySqlMigrationDropTableCapability
import gog.my_project.data_base.migration.renderer.nodes.rename_table.MySqlMigrationRenameTableCapability
import gog.my_project.data_base.migration.renderer.nodes.rename_column.MySqlMigrationRenameColumnCapability
import gog.my_project.data_base.migration.renderer.nodes.modify_column.MySqlMigrationModifyColumnCapability
import gog.my_project.data_base.migration.renderer.nodes.create_index.MySqlMigrationCreateIndexCapability
import gog.my_project.data_base.migration.renderer.nodes.drop_index.MySqlMigrationDropIndexCapability
import gog.my_project.data_base.migration.renderer.nodes.create_unique_index.MySqlMigrationCreateUniqueIndexCapability
import gog.my_project.data_base.migration.renderer.nodes.create_multi_column_index.MySqlMigrationCreateMultiColumnIndexCapability
import gog.my_project.data_base.migration.renderer.nodes.create_full_text_index.MySqlMigrationCreateFullTextIndexCapability
import gog.my_project.data_base.migration.renderer.nodes.create_spatial_index.MySqlMigrationCreateSpatialIndexCapability
import gog.my_project.data_base.migration.renderer.nodes.create_foreign_key.MySqlMigrationCreateForeignKeyCapability
import gog.my_project.data_base.migration.renderer.nodes.drop_foreign_key.MySqlMigrationDropForeignKeyCapability
import gog.my_project.data_base.migration.dialect.nodes.rename_column.IRenameColumnSqlDialect
import gog.my_project.data_base.migration.dialect.nodes.rename_column.RenameColumnSqlStrategy

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
        ast: IMigrationRenameColumnAst,
        definitionSuffix: String,
    ): String? {
        check(renameColumnSqlStrategy == RenameColumnSqlStrategy.CHANGE_COLUMN) {
            "Configured target selects native RENAME COLUMN, not CHANGE COLUMN"
        }
        return MySqlMigrationRenameColumnCapability().renderLegacy(ast, definitionSuffix)
    }

    override val _prefixCreateTable: String = "CREATE TABLE"
    override fun registerRenders() {

        /// create table
        _registry.register(IMigrationRenderCreateTableAst::class       , MySqlMigrationRenderCreateTableCapability());
        _registry.register(IMigrationTableAst::class                   , MySqlMigrationTableCapability());
        _registry.register(IMigrationColumnAst::class                  , MySqlMigrationColumnCapability());
        _registry.register(IMigrationCreateTableDefinitionAst::class, MySqlMigrationCreateTableDefinitionCapability());

        /// drop table
        _registry.register(IMigrationDropTableAst::class                , MySqlMigrationDropTableCapability());

        /// rename table
        _registry.register(IMigrationRenameTableAst::class               , MySqlMigrationRenameTableCapability());
        _registry.register(IMigrationRenameColumnAst::class              , MySqlMigrationRenameColumnCapability());

        /// add column
        _registry.register(IMigrationAddColumnAst::class                  , MySqlMigrationAddColumnCapability());

        /// drop column
        _registry.register(IMigrationDropColumnAst::class                  , MySqlMigrationDropColumnCapability());

        /// modify column
        _registry.register(IMigrationModifyColumnAst::class, MySqlMigrationModifyColumnCapability());

        /// create index
        _registry.register(IMigrationCreateIndexAst::class, MySqlMigrationCreateIndexCapability());

        /// drop index
        _registry.register(IMigrationDropIndexAst::class, MySqlMigrationDropIndexCapability());

        /// create unique index
        _registry.register(IMigrationCreateUniqueIndexAst::class, MySqlMigrationCreateUniqueIndexCapability());

        /// create multi-column index
        _registry.register(IMigrationCreateMultiColumnIndexAst::class, MySqlMigrationCreateMultiColumnIndexCapability());

        /// create full-text index
        _registry.register(IMigrationCreateFullTextIndexAst::class, MySqlMigrationCreateFullTextIndexCapability());

        /// create spatial index
        _registry.register(IMigrationCreateSpatialIndexAst::class, MySqlMigrationCreateSpatialIndexCapability());

        /// foreign keys
        _registry.register(IMigrationCreateForeignKeyAst::class, MySqlMigrationCreateForeignKeyCapability());
        _registry.register(IMigrationDropForeignKeyAst::class, MySqlMigrationDropForeignKeyCapability());

    }

}
