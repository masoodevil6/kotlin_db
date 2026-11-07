package gog.my_project.data_base.migration.example.v1.migrations.modify_column

import gog.my_project.data_base_schema.*





class A1ExampleModifyColumnV1(
    private val table: String = "users",
    private val column: String = "name",
) {
    fun migration(): SchemaOperation =
        Schema.modifyColumn {
            tableName(table)
            name(column)
            dataType(SchemaDataType.VarcharType(255))
            notNull()
            default("Unknown")
            autoIncrement(false)
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("MODIFY COLUMN example")
        println("---------------------------")
        println(sql)
    }

    }
