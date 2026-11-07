# Schema عملیات

Schema API برای تعریف عملیات DDL در ماژول `data_base:operation:schema` قرار دارد. DatabaseManager فعلاً از طریق `DatabaseHandle`، اجرای مستقیم Schema را ارائه نمی‌کند و runtime قدیمی Migration نیز حذف شده است.

تعریف عملیات همچنان با Schema DSL انجام می‌شود؛ برای نمونه:

```kotlin
val createCustomers: SchemaOperation = Schema.createTable {
    table { tableName("customers") }
    addColumn {
        name("id")
        dataType(SchemaDataType.IntType)
        notNull()
        autoIncrement()
        primaryKey()
    }
}
```

برای رندر نمونه‌های Schema بدون اتصال به دیتابیس، ماژول `data_base:migration:example` را ببینید. اجرای DDL از طریق Manager تا آماده‌شدن مسیر اجرایی جدید در دسترس نیست.
