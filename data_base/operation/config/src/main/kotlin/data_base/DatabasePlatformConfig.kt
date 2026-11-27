package gog.my_project.data_base_operation.config.data_base

import gog.my_project.data_base.dialect.DatabaseDialect

/** SQL dialect and configured target version for one database platform. */
data class DatabasePlatformConfig(
    val dialect: DatabaseDialect = DatabaseDialect.MYSQL,
    val targetDatabaseVersion: ProductDatabaseVersion? = null,
)
