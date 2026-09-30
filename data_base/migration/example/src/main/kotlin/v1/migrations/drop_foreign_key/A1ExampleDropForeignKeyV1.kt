package gog.my_project.data_base.migration.example.v1.migrations.drop_foreign_key

import gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi
import gog.my_project.data_base.migration.builder.dropForeignKey
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleDropForeignKeyV1 {
    fun migration(): IMigrationDropForeignKeyApi =
        dropForeignKey {
            tableName("orders")
            name("fk_orders_tenant_user")
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("DROP FOREIGN KEY example")
        println("---------------------------")
        println(sql)
    }

    /** Execution is opt-in: call this method explicitly after reviewing the rendered DDL. */
    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("drop foreign key query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("drop foreign key execute: $result") },
        )
    }
}
