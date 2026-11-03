package gog.my_project.data_base.core.data_base

import gog.my_project.data_base.core.query.dialect.DialectQuery

/** SQL dialect and configured target version for one database platform. */
data class DatabasePlatformConfig(
    val dialect: DialectQuery = DialectQuery.MY_SQL,
    val targetDatabaseVersion: ProductDatabaseVersion? = null,
)
