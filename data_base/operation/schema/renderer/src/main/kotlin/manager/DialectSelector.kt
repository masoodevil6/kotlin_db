package gog.my_project.data_base_schema.renderer.manager

import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_operation.config.data_base.ProductDatabaseVersion
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import gog.my_project.data_base_schema.renderer.dialects.MySqlDialect
import gog.my_project.data_base_schema.renderer.dialects.MariaDbDialect

class DialectSelector{

    private val dialects: Map<DatabaseDialect, (ProductDatabaseVersion?) -> ISqlDialect> = mapOf(
        DatabaseDialect.MYSQL to { version -> MySqlDialect(version) },
        DatabaseDialect.MARIA_DB to { version -> MariaDbDialect(version) },
    )

    fun select(type: DatabaseDialect, targetVersion: ProductDatabaseVersion? = null): ISqlDialect {
        return (dialects[type] ?: error("dialect not registered: $type"))(targetVersion)
    }

}
