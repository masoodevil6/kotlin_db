package gog.my_project.data_base.migration.example.v1.migrations.create_table_timestamps

import gog.my_project.data_base_schema.*

import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1




class A1ExampleCreateTableTimestampsV1 : IExampleV1 {
    override fun migration(): SchemaOperation = Schema.createTable {
        table { tableName("posts") }
        addColumn {
            name("id")
            dataType(SchemaDataType.IntType)
            notNull()
            autoIncrement()
            primaryKey()
        }
        timestamps()
        softDeletes()
    }
}
