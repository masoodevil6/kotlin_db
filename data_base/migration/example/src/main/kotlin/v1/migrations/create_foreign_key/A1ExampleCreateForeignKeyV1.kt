package gog.my_project.data_base.migration.example.v1.migrations.create_foreign_key

import gog.my_project.data_base_schema.*





class A1ExampleCreateForeignKeyV1 {
    fun migration(): SchemaOperation =
        Schema.createForeignKey {
            tableName("orders")
            name("fk_orders_tenant_user")
            columns("tenant_id", "user_id")
            referencesTable("users")
            referencesColumns("tenant_id", "id")
            onDelete(SchemaForeignKeyAction.CASCADE)
            onUpdate(SchemaForeignKeyAction.RESTRICT)
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("CREATE FOREIGN KEY example")
        println("---------------------------")
        println(sql)
    }

    /** Execution is opt-in: call this method explicitly after reviewing the rendered DDL. */
    }
