# ثبت و دسترسی به چند پایگاه داده

هر فراخوانی `database(key)` در DSL یک Registration با تنظیمات خودش به catalog اضافه می‌کند. در نتیجه برای هر پایگاه داده یک Key، یک `DatabaseConfig` و در صورت نیاز یک Migration Configuration جدا تعریف کنید.

```kotlin
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi

object MainDatabase : DatabaseKey
object AnalyticsDatabase : DatabaseKey

val mainConfig = buildMainConfig()
val analyticsConfig = buildAnalyticsConfig()

val manager: DatabaseManagerApi = DatabaseManager {
    database(MainDatabase) {
        config = mainConfig
    }
    database(AnalyticsDatabase) {
        config = analyticsConfig
    }
}

try {
    val main: DatabaseHandleApi = manager[MainDatabase]
    val analytics: DatabaseHandleApi = manager[AnalyticsDatabase]

    println(manager.registeredKeys())
    // عملیات MAIN را از main و عملیات ANALYTICS را از analytics فراخوانی کنید.
    // نمونه‌های connection { ... } و transaction { ... } در راهنماهای Executor هستند.
} finally {
    manager.shutdown()
}
```

در این قطعه، `mainConfig` و `analyticsConfig` را مطابق [راهنمای تنظیمات اتصال](database-configuration.md) ساخته‌اید. هر کدام باید تنظیمات پایگاه دادهٔ مقصد خودش را داشته باشد؛ برای نمونه، `mainConfig.connection.dbName` و `analyticsConfig.connection.dbName` می‌توانند به schemaهای جدا اشاره کنند.

## نکته‌های چند Registration

- تنظیمات هر کلید در بلوک همان `database(key) { ... }` قرار می‌گیرد. تنظیمات Migration نیز، در صورت استفاده از ماژول Migration، باید به همان Registration متصل شود.
- `manager[MainDatabase]` یک `DatabaseHandleApi` برای کلیدی برمی‌گرداند که با آن برابر است. کلید ثبت‌نشده باعث `NoSuchElementException` می‌شود.
- گرفتن Handle یا فهرست `registeredKeys()` به‌تنهایی Connection یا Database Context را باز نمی‌کند. Context هر Registration هنگام اولین عملیات آن به‌صورت lazy ساخته می‌شود.
- Handle اتصال خام JDBC را افشا نمی‌کند. از `connection { ... }` یا `transaction { ... }` خود Handle استفاده کنید و executor داخل callback را نگه ندارید.
- `DatabaseManager` عملیات پذیرفته‌شده را هنگام `shutdown()` به پایان می‌رساند و سپس Contextهایی را که خودش ساخته است می‌بندد. Manager و Handleهایش را بعد از پایان shutdown استفاده نکنید.
- Manager بین Registrationها قفل سراسری نمی‌گیرد؛ عملیات عادی آن‌ها می‌توانند مستقل هم‌زمان اجرا شوند. Migrationها فقط وقتی با قفل مشترک هماهنگ می‌شوند که `DatabaseIdentity` یکسانی بسازند. جدا بودن Registration تضمین نمی‌کند مقصد فیزیکی آن‌ها جدا باشد؛ مقصد و منابع مشترک را در تنظیمات و سیاست برنامه بررسی کنید.

تفاوت Manager lease، قفل Migration و قفل دیتابیس در [راهنمای Lifecycle و هم‌زمانی](../../limitations/lifecycle-and-errors.md) توضیح داده شده است.

برای نمونهٔ اجرایی با دو کلید مستقل و دو تنظیم Migration، فایل [`example/README.md`](../../../../example/README.md) را هم ببینید.
