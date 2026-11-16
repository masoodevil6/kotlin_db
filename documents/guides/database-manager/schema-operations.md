# تعریف Schema

Schema API عملیات DDL را تعریف می‌کند. برنامهٔ مصرف‌کننده آن‌ها را در `Migration.up()` و `Migration.down()` برمی‌گرداند؛ برای اجرای عادی DDL از مسیر Migration استفاده کنید. `DatabaseHandle` اجرای مستقیم Schema را در Connection یا Transaction مصرف‌کننده عرضه نمی‌کند.

```kotlin
class CreateCustomers : Migration(AppMigrationTags.Items.CREATE_CUSTOMERS) {
    override fun up(): MigrationDefinition = migration {
        operation(Schema.createTable {
            table { tableName("customers") }
            addColumn {
                name("id")
                dataType(SchemaDataType.IntType)
                notNull()
                autoIncrement()
                primaryKey()
            }
        })
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropTable { tableName("customers") })
    }
}
```

Migration برای Connection و اجرای Schema از Operation Scope همان Handle استفاده می‌کند. در مسیر رسمی برای هر Version Transaction مستقلی ساخته نمی‌شود؛ Atomicity کلی یا rollbackپذیری همهٔ DDLها تضمین نشده است و به رفتار دیتابیس وابسته است. جزئیات چرخه در [راهنمای Migration](../migration/execution.md) آمده است.

ماژول `data_base:migration:example` نمونه‌های Schema را به SQL رندر می‌کند و به دیتابیس متصل نمی‌شود. نمونهٔ اجرای opt-in API عمومی در [example ریشهٔ پروژه](../../../example/README.md) قرار دارد.
