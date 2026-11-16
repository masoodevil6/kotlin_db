# شروع به کار با DatabaseManager

مصرف‌کننده به ماژول `:data_base:manager` وابسته می‌شود و برای هر دیتابیس یک `DatabaseKey` و `DatabaseConfig` ثبت می‌کند:

```kotlin
object MainDatabase : DatabaseKey
data class AnalyticsDatabase(val region: String) : DatabaseKey

val manager: DatabaseManagerApi = DatabaseManager {
    database(MainDatabase) {
        config = mainDatabaseConfig
    }
    database(AnalyticsDatabase("eu-west")) {
        config = analyticsDatabaseConfig
    }
}

try {
    val main = manager[MainDatabase]
    val analytics = manager[AnalyticsDatabase("eu-west")]

    // Query و Transaction از Manager می‌آیند؛ برای Migration به Aggregate migration وابسته شوید.
    println(manager.registeredKeys())
} finally {
    manager.shutdown()
}
```

Context دیتابیس هنگام دریافت Handle ساخته نمی‌شود؛ نخستین عملیات آن را به‌صورت lazy مقداردهی می‌کند. `shutdown()` پذیرش عملیات جدید را متوقف می‌کند و پس از پایان عملیات پذیرفته‌شده منابع را می‌بندد.

برای API Migration عمومی، dependency زیر را اضافه کنید و `migrationConfiguration` را به همان registration متصل کنید:

```kotlin
dependencies {
    implementation(project(":data_base:migration"))
}
```

نمونهٔ کامل Query و Transaction در `data_base:manager:example` قرار دارد. اجرای آن opt-in است و Queryهای نمونه فرض می‌کنند جدول‌های مقصد از قبل وجود دارند. برای ثبت Configuration در هر Registration و اجرای Migration از Handle به [راهنمای Migration](migrations.md) مراجعه کنید.
