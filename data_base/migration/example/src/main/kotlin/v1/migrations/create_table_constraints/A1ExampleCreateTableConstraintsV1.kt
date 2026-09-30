package gog.my_project.data_base.migration.example.v1.migrations.create_table_constraints

import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.TextType
import gog.my_project.data_base.migration.params.data_types.VarcharType

class A1ExampleCreateTableConstraintsV1 : IExampleV1 {
    override fun migration(): IMigrationRenderCreateTableApi = createTable {
        table { tableName("orders") }

        addColumn {
            name("tenant_id")
            dataType(IntType())
            notNull()
        }
        addColumn {
            name("order_id")
            dataType(IntType())
            notNull()
        }
        addColumn {
            name("user_id")
            dataType(IntType())
            notNull()
        }
        addColumn {
            name("email")
            dataType(VarcharType(255))
            notNull()
        }
        addColumn {
            name("description")
            dataType(TextType())
            notNull()
        }

        primaryKey {
            columns("tenant_id", "order_id")
        }
        unique {
            name("uq_orders_email")
            columns("email")
        }
        index {
            name("idx_orders_tenant_user")
            columns("tenant_id", "user_id")
            using(IndexMethod.BTREE)
        }
        fullTextIndex {
            name("ft_orders_description")
            columns("description")
        }
        foreignKey {
            name("fk_orders_user")
            columns("user_id")
            referencesTable("users")
            referencesColumns("id")
            onDelete(ForeignKeyAction.CASCADE)
            onUpdate(ForeignKeyAction.RESTRICT)
        }
    }
}
