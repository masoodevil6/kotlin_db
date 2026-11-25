# تعریف کلاس Migration

هر Migration از کلاس `Migration` ارث می‌برد، یک `MigrationItemTag` را به سازنده می‌دهد و دو عملیات `up()` و `down()` را تعریف می‌کند.

```kotlin
import gog.my_project.data_base_migration.api.builder.migration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType

class CreateCustomers : Migration(AppMigrationTags.Items.CREATE_CUSTOMERS) {
    override fun up(): MigrationDefinition = migration {
        operation(
            Schema.createTable {
                table { tableName("customers") }
                addColumn {
                    name("id")
                    dataType(SchemaDataType.IntType)
                    notNull()
                    autoIncrement()
                    primaryKey()
                }
                addColumn {
                    name("name")
                    dataType(SchemaDataType.VarcharType(200))
                    notNull()
                }
            },
        )
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropTable { tableName("customers") })
    }
}

class AddCustomerEmail : Migration(AppMigrationTags.Items.ADD_CUSTOMER_EMAIL) {
    override fun up(): MigrationDefinition = migration {
        operation(
            Schema.addColumn {
                tableName("customers")
                name("email")
                dataType(SchemaDataType.VarcharType(255))
                notNull()
            },
        )
    }

    override fun down(): MigrationDefinition = migration {
        operation(
            Schema.dropColumn {
                tableName("customers")
                name("email")
            },
        )
    }
}
```

`up()` تغییر Schema در مسیر اعمال Version را تعریف می‌کند و `down()` تغییر برگشت را. هر `MigrationDefinition` دقیقاً یک operation سطح‌بالای `Schema` می‌پذیرد؛ صفر یا بیش از یک فراخوانی `operation(...)` هنگام ساخت Definition خطا می‌دهد. برای چند تغییر مستقل، آن‌ها را در چند Migration جداگانه ثبت کنید.

ساخت Migration و Definition به‌تنهایی آن را اجرا نمی‌کند؛ اجرای واقعی از فرمان‌های Migration روی Database Handle انجام می‌شود. کلاس باید سازندهٔ بدون آرگومان داشته باشد، چون Configuration برای خواندن هویت، نمونه‌ای از کلاس می‌سازد. بنابراین سازنده را بدون اثر جانبی نگه دارید.
