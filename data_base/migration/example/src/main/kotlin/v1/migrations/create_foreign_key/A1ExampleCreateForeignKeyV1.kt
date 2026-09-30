package gog.my_project.data_base.migration.example.v1.migrations.create_foreign_key

import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.builder.createForeignKey
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleCreateForeignKeyV1 {
    fun migration(): IMigrationCreateForeignKeyApi =
        createForeignKey {
            tableName("orders")
            name("fk_orders_tenant_user")
            columns("tenant_id", "user_id")
            referencesTable("users")
            referencesColumns("tenant_id", "id")
            onDelete(ForeignKeyAction.CASCADE)
            onUpdate(ForeignKeyAction.RESTRICT)
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("CREATE FOREIGN KEY example")
        println("---------------------------")
        println(sql)
    }

    /** Execution is opt-in: call this method explicitly after reviewing the rendered DDL. */
    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("create foreign key query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("create foreign key execute: $result") },
        )
    }
}
