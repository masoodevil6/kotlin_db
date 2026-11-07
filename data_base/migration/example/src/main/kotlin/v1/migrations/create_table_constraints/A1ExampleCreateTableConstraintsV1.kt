package gog.my_project.data_base.migration.example.v1.migrations.create_table_constraints

import gog.my_project.data_base_schema.*

import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1




class A1ExampleCreateTableConstraintsV1 : IExampleV1 {
    override fun migration(): SchemaOperation = Schema.createTable {
        table { tableName("orders") }

        addColumn {
            name("tenant_id")
            dataType(SchemaDataType.IntType)
            notNull()
        }
        addColumn {
            name("order_id")
            dataType(SchemaDataType.IntType)
            notNull()
        }
        addColumn {
            name("user_id")
            dataType(SchemaDataType.IntType)
            notNull()
        }
        addColumn {
            name("email")
            dataType(SchemaDataType.VarcharType(255))
            notNull()
        }
        addColumn {
            name("description")
            dataType(SchemaDataType.TextType)
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
            using(SchemaIndexMethod.BTREE)
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
            onDelete(SchemaForeignKeyAction.CASCADE)
            onUpdate(SchemaForeignKeyAction.RESTRICT)
        }
    }
}
