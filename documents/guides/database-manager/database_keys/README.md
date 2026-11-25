# کلیدهای پایگاه داده

`DatabaseKey` یک شناسهٔ نوع‌دار برای انتخاب Registration در `DatabaseManager` است. هنگام تعریف catalog، هر کلید به تنظیمات یک پایگاه داده وصل می‌شود؛ هنگام استفاده، همان کلید را به `manager[key]` می‌دهید تا `DatabaseHandleApi` همان Registration را بگیرید.

```kotlin
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi

object MainDatabase : DatabaseKey
object AnalyticsDatabase : DatabaseKey

val manager: DatabaseManagerApi = DatabaseManager {
    database(MainDatabase) {
        config = mainConfig
    }
    database(AnalyticsDatabase) {
        config = analyticsConfig
    }
}

val main = manager[MainDatabase]
val analytics = manager[AnalyticsDatabase]
```

در این مثال، `mainConfig` و `analyticsConfig` باید جداگانه و مطابق مقصد هر اتصال ساخته شده باشند. کلید فقط Registration را پیدا می‌کند و خودش نشانی سرور، نام پایگاه داده یا تنظیمات اتصال را نگه نمی‌دارد.

## انتخاب نوع کلید

برای تعداد ثابتی از پایگاه داده‌ها، `object` انتخاب ساده‌ای است: هر `object` یک نمونهٔ یکتا می‌دهد و در همهٔ محل‌های برنامه می‌توانید همان نام را برای ثبت و lookup به کار ببرید.

برای کلیدهای پارامتری، از `data class` با propertyهای immutable استفاده کنید:

```kotlin
data class TenantDatabase(val tenantId: String) : DatabaseKey

val manager = DatabaseManager {
    database(TenantDatabase("tenant-a")) {
        config = tenantAConfig
    }
    database(TenantDatabase("tenant-b")) {
        config = tenantBConfig
    }
}

val tenantA = manager[TenantDatabase("tenant-a")]
val tenantB = manager[TenantDatabase("tenant-b")]
```

`data class` بر اساس مقادیر propertyهایش `equals` و `hashCode` می‌سازد؛ بنابراین نمونهٔ تازهٔ `TenantDatabase("tenant-a")` با کلید ثبت‌شدهٔ هم‌مقدار برابر است و همان Handle را پیدا می‌کند. propertyهایی را که در برابری کلید دخیل‌اند هنگام استفادهٔ Manager تغییر ندهید؛ ناپایداری `hashCode` می‌تواند lookup و تشخیص Registration تکراری را مختل کند.

اگر کلاس معمولی را بدون بازنویسی `equals` و `hashCode` به‌عنوان کلید به کار ببرید، برابری آن بر اساس reference خواهد بود. در چنین حالتی ساختن نمونهٔ تازه برای lookup، کلید ثبت‌شده را پیدا نمی‌کند؛ reference کلید را نگه دارید یا از `object`/`data class` استفاده کنید.

## قواعد Registration و lookup

- هر `database(key) { ... }` یک Registration می‌سازد و باید `config` آن را مقداردهی کند.
- در یک Manager، ثبت دوبارهٔ کلیدی که با کلید قبلی برابر باشد رد می‌شود.
- `manager[key]` بر اساس `equals` و `hashCode` کلید را پیدا می‌کند. کلید ثبت‌نشده باعث `NoSuchElementException` می‌شود.
- دو کلید متفاوت می‌توانند حتی به `DatabaseConfig`های برابر وصل شوند؛ هویت Registration از کلید می‌آید، مقصد اتصال از تنظیمات.
- `registeredKeys()` تصویر کلیدهای ثبت‌شده را به ترتیب ورود برمی‌گرداند. گرفتن کلیدها یا Handle به‌تنهایی Connection را باز نمی‌کند؛ Context هر Registration هنگام نخستین عملیات آن به‌شکل lazy ساخته می‌شود.
- Handle به همان Registration تعلق دارد. برای اجرای Connection یا Transaction از متدهای Handle استفاده کنید؛ شروع کار در [راهنمای Executor](../executor/README.md) و جزئیات در [راهنمای Connection](../executor/connection.md) و [راهنمای Transaction](../executor/transaction.md) آمده است.

برای تعریف تنظیمات اتصال هر کلید، [راهنمای تعریف و تنظیم چند پایگاه داده](../database_define/README.md) را ببینید.

برای الصاق metadata نوع‌دار به هر Registration و خواندن آن از Handle، [راهنمای Registration Attributes](../registration_attributes/README.md) را ببینید.
