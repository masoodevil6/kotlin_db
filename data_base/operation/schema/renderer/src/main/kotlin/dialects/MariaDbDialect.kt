package gog.my_project.data_base_schema.renderer.dialects

import gog.my_project.data_base_operation.config.data_base.ProductDatabaseVersion
import gog.my_project.data_base_operation.config.data_base.DatabaseProduct
import gog.my_project.data_base_operation.config.data_base.MARIA_DB
import gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst
import gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst
import gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst
import gog.my_project.data_base_schema.renderer.nodes.drop_column.MySqlSchemaDropColumnCapability
import gog.my_project.data_base_schema.renderer.nodes.drop_foreign_key.MySqlSchemaDropForeignKeyCapability
import gog.my_project.data_base_schema.renderer.nodes.drop_index.MySqlSchemaDropIndexCapability

/** MariaDB shares the common MySQL grammar and overrides MariaDB-specific IF EXISTS clauses. */
class MariaDbDialect(private val schemaTargetVersion: ProductDatabaseVersion? = null) :
    MySqlDialect(schemaTargetVersion, DatabaseProduct.MARIA_DB) {
    init {
        _registry.register(
            ISchemaDropColumnAst::class,
            MySqlSchemaDropColumnCapability(supportsIfExists = supportsIfExistsSince(MARIA_DB.Version(10, 0, 2))),
        )
        _registry.register(
            ISchemaDropIndexAst::class,
            MySqlSchemaDropIndexCapability(supportsIfExists = supportsIfExistsSince(MARIA_DB.Version(10, 1, 4))),
        )
        _registry.register(
            ISchemaDropForeignKeyAst::class,
            MySqlSchemaDropForeignKeyCapability(supportsIfExists = supportsIfExistsSince(MARIA_DB.Version(10, 0, 2))),
        )
    }

    private fun supportsIfExistsSince(minimumVersion: ProductDatabaseVersion): Boolean =
        schemaTargetVersion == null ||
            (schemaTargetVersion.product == DatabaseProduct.MARIA_DB && schemaTargetVersion >= minimumVersion)
}
