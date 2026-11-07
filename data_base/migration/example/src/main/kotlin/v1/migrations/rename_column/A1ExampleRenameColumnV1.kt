package gog.my_project.data_base.migration.example.v1.migrations.rename_column

import gog.my_project.data_base_schema.*





class A1ExampleRenameColumnV1(
    private val table: String = "users",
    private val from: String = "display_name",
    private val target: String = "full_name",
) {
    fun migration(): SchemaOperation = Schema.renameColumn {
        tableName(table)
        name(from)
        to(target)
    }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("RENAME COLUMN example")
        println("---------------------------")
        println(sql)
    }

    }
