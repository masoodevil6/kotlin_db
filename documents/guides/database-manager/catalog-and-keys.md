# Catalog, Registration و کلیدها

`DatabaseManager { ... }` یک catalog از دیتابیس‌ها می‌سازد. هر بلوک `database(key) { ... }` یک `DatabaseConfig` دریافت می‌کند:

```kotlin
val manager: DatabaseManagerApi = DatabaseManager {
    database(MainDatabase) {
        config = mainDatabaseConfig
    }
    database(AnalyticsDatabase("eu-west")) {
        config = analyticsDatabaseConfig
    }
}

val main: DatabaseHandleApi = manager[MainDatabase]
val analytics: DatabaseHandleApi = manager[AnalyticsDatabase("eu-west")]
val keys: Set<DatabaseKey> = manager.registeredKeys()
```

`DatabaseKey` مستقل از `DatabaseConfig` است. هویت کلید بر اساس `equals` و `hashCode` تعیین می‌شود؛ کلیدهای برابر به یک registration می‌رسند. برای `data class` از فیلدهای immutable و پایدار استفاده کنید.

گرفتن Handle یا فراخوانی `registeredKeys()` به‌تنهایی Context یا اتصال JDBC ایجاد نمی‌کند. Context هر registration هنگام نخستین عملیات مقداردهی می‌شود. lookup کلید ثبت‌نشده خطای مشخص می‌دهد و پس از شروع shutdown، عملیات جدید پذیرفته نمی‌شود.

هر Handle در حال حاضر دسترسی Query و Transaction دارد. اجرای Migration از این catalog و Handle موقتاً حذف شده است؛ جزئیات در [Migration و DatabaseManager](migrations.md) آمده است.
