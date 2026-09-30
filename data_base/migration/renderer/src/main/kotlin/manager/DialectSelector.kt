package gog.my_project.data_base.migration.renderer.manager

import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.data_base.ProductDatabaseVersion
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import gog.my_project.data_base.migration.renderer.dialects.MariaDbDialect

class DialectSelector{

    private val dialects: Map<DialectQuery, (ProductDatabaseVersion?) -> ISqlDialect> = mapOf(
        DialectQuery.MY_SQL to { version -> MySqlDialect(version) },
        DialectQuery.MARIA_DB to { version -> MariaDbDialect(version) },
    )

    fun select(type: DialectQuery, targetVersion: ProductDatabaseVersion? = null): ISqlDialect {
        return (dialects[type] ?: error("dialect not registered: $type"))(targetVersion)
    }

}
