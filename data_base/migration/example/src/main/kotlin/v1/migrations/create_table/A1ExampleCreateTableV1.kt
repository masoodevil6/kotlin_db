package gog.my_project.data_base.migration.example.v1.migrations.create_table

import gog.my_project.data_base_schema.*

import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1




class A1ExampleCreateTableV1(
    private val useIfNotExists: Boolean = false,
) : IExampleV1 {
    override fun migration(): SchemaOperation =
        Schema.createTable {
            table { tableName("users") }
            if (useIfNotExists) ifNotExists()
            addColumn {
                name("id")
                dataType(SchemaDataType.IntType)
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("name")
                dataType(SchemaDataType.VarcharType(120))
                notNull()
            }
            addColumn {
                name("display_name")
                dataType(SchemaDataType.VarcharType(120))
                default("New user")
            }
        }
}
