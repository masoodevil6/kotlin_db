package gog.my_project.data_base.migration.example.v1.migrations.create_table

import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.builder.ast.create_table.render_schema_create_table.SchemaRenderCreateTableBuilder
import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base_schema.params.data_types.IntType
import gog.my_project.data_base_schema.params.data_types.VarcharType




class A1ExampleCreateTableV1(
    private val useIfNotExists: Boolean = false,
) : IExampleV1 {
    override fun migration(): ISchemaRenderCreateTableApi =
        SchemaRenderCreateTableBuilder().apply {
            table { tableName("users") }
            if (useIfNotExists) ifNotExists()
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("name")
                dataType(VarcharType(120))
                notNull()
            }
            addColumn {
                name("display_name")
                dataType(VarcharType(120))
                default("New user")
            }
        }
}
