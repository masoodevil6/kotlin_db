package gog.my_project.data_base.migration.example.v1.migrations.create_table_timestamps

import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.builder.ast.create_table.render_schema_create_table.SchemaRenderCreateTableBuilder
import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base_schema.params.data_types.IntType




class A1ExampleCreateTableTimestampsV1 : IExampleV1 {
    override fun migration(): ISchemaRenderCreateTableApi = SchemaRenderCreateTableBuilder().apply {
        table { tableName("posts") }
        addColumn {
            name("id")
            dataType(IntType())
            notNull()
            autoIncrement()
            primaryKey()
        }
        timestamps()
        softDeletes()
    }
}
