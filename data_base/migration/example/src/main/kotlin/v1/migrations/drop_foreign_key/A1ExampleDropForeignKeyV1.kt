package gog.my_project.data_base.migration.example.v1.migrations.drop_foreign_key

import gog.my_project.data_base_schema.*





class A1ExampleDropForeignKeyV1 {
    fun migration(): SchemaOperation =
        Schema.dropForeignKey {
            tableName("orders")
            name("fk_orders_tenant_user")
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("DROP FOREIGN KEY example")
        println("---------------------------")
        println(sql)
    }

    /** Execution is opt-in: call this method explicitly after reviewing the rendered DDL. */
    }
