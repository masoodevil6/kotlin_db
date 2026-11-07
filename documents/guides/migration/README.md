# Migration

## وضعیت فعلی

قراردادهای تعریف Migration و ثبت آن‌ها در ماژول‌های `data_base:migration:core` و `data_base:migration:api` باقی مانده‌اند. پیاده‌سازی قدیمی اجرا، خواندن و ثبت history حذف شده است؛ در نتیجه `DatabaseHandle` فعلاً Migration اجرا نمی‌کند و جداول سیستمی Migration نیز توسط این کتابخانه مدیریت نمی‌شوند.

فایل‌های `data_base:migration:example` فقط نمونه‌های Schema را می‌سازند و SQL را رندر می‌کنند؛ این مثال‌ها به پایگاه داده متصل نمی‌شوند.

## تعریف Migration

هر Migration می‌تواند تعریف `up` و `down` خود را با Schema API بسازد. هر Definition یک `SchemaOperation` دارد:

```kotlin
@MigrationId("create_customers")
class CreateCustomers : Migration {
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

ثبت Migrationها و گروه‌بندی آن‌ها با `migrationConfig { ... }` همچنان برای ساخت Configuration در دسترس است. این Configuration فعلاً به Manager متصل نیست و مسیر اجرای آن هنوز پیاده‌سازی نشده است.

برای جزئیات تعریف و ثبت، به [راهنمای Registration](registration.md) مراجعه کنید.
