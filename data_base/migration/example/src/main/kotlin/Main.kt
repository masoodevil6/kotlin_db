package gog.my_project.data_base.migration.example

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.data_base.MARIA_DB
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.migration.example.managers.ManagerExampleV1
import gog.my_project.data_base.migration.executor.manager.MigrationExecutor
import gog.my_project.data_base.migration.renderer.manager.DialectSelector

fun main() {
    DefaultDatabaseConfig.config = DatabaseConfig(
        dbDomain =              System.getenv("DB_DOMAIN") ?: "jdbc:mysql://127.0.0.1",
        dbPort =                (System.getenv("DB_PORT") ?: "3306").toInt(),
        dbName =                System.getenv("DB_NAME") ?: "kotlin_db",
        dbUserName =            System.getenv("DB_USER") ?: "root",
        dbPassword =            System.getenv("DB_PASSWORD") ?: "",
        dialect =               DialectQuery.MARIA_DB,
        targetDatabaseVersion = MARIA_DB.Version(10, 4, 28),
    )

    val managerV1 = ManagerExampleV1(
        statusRunCreateTable =              false,
        statusRunDropTable =                false,
        statusRunRenameTable =              false,
        statusRunAddColumn =                false,
        statusRunDropColumn =               false,
        statusRunRenameColumn =             false,
        statusRunModifyColumn =             false,
        statusRunCreateIndex =              false,
        statusRunDropIndex =                false,
        statusRunCreateUniqueIndex =        false,
        statusRunCreateFullTextIndex =      false,
        statusRunCreateSpatialIndex=        false,
        statusRunCreateMultiColumnIndex =   false,
        statusRunCreateForeignKey =         true,
        statusRunDropForeignKey =           false,
        statusExecute =                     true,
    )

    managerV1.readyListExamples()
    managerV1.renderExamples(
        dialect =               DialectSelector().select(
            DefaultDatabaseConfig.config.dialect,
            DefaultDatabaseConfig.config.targetDatabaseVersion,
        ),
        migrationExecutor =     MigrationExecutor(),
    )
}
