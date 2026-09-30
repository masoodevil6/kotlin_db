package gog.my_project.data_base.migration.example.v1.migrations.create_table

import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.VarcharType

class A1ExampleCreateTableV1(
    private val useIfNotExists: Boolean = false,
) : IExampleV1 {
    override fun migration(): IMigrationRenderCreateTableApi =
        createTable {
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
