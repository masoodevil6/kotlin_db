# kotlin_db

`kotlin_db` مجموعه‌ای از ماژول‌های Kotlin برای ساخت Queryهای SQL، اجرای آن‌ها با Connection و Transaction مدیریت‌شده، کار با چند پایگاه داده و تعریف و اجرای Migration است. این راهنما نقطهٔ شروع مصرف‌کنندگان کتابخانه است؛ قراردادهای کامل هر قابلیت در راهنماهای تخصصی آمده‌اند.

## فهرست

- [قابلیت‌ها و انتخاب ماژول](#capabilities)
- [شروع سریع](#quick-start)
- [مسیرهای رایج](#common-workflows)
- [نکته‌های مهم استفاده](#usage-notes)
- [راهنماها](#guides)
- [MCP](#mcp)

<a id="capabilities"></a>
## قابلیت‌ها و انتخاب ماژول

هر بخش را بر اساس کاری که برنامه انجام می‌دهد انتخاب کنید:

| نیاز | ماژول در این مخزن | کاربرد |
|---|---|---|
| ثبت یک یا چند پایگاه داده و گرفتن Handle | `:data_base:manager` | تعریف Registrationهای typed و اجرای عملیات از Handle هر Registration |
| ساخت و اجرای Query | `:data_base:operation:query:builder` و `:data_base:operation:query:executor` | ساخت Query با DSL و اجرای آن از مسیر Executor |
| تعریف و اجرای Migration از Handle | `:data_base:migration` | ثبت `MigrationConfiguration` برای Registration و استفاده از `init`، `migrate`، `rollback` و APIهای خواندن وضعیت |
| استفاده از lifecycle سطح پایین Operation | `:data_base:operation:executor` | کنترل Scope، Connection، Transaction، رویدادها و Pipelineهای Operation |
| کار با Collectionهای نتیجه | `:collection:core` | APIهای Collection پروژه و تبدیل نتیجه‌ها |

مسیرهای بالا نام ماژول‌های داخل همین مخزن‌اند. مختصات انتشارشدهٔ Maven در این Repository مشخص نشده است؛ برای مصرف از بیرون مخزن، مختصات Artifact را از کانال انتشار پروژه دریافت کنید.

<a id="quick-start"></a>
## شروع سریع

در یک build چندماژولهٔ همین Repository، برای گرفتن Handle مدیریت‌شده به `:data_base:manager` وابسته شوید:

```kotlin
dependencies {
    implementation(project(":data_base:manager"))
}
```

ابتدا تنظیمات اتصال را بسازید، سپس برای هر پایگاه داده یک کلید تعریف و آن را در Manager ثبت کنید. هر Registration با کلید خودش قابل دسترسی است:

```kotlin
object MainDatabase : DatabaseKey

data class AnalyticsDatabase(val region: String) : DatabaseKey

val manager: DatabaseManagerApi = DatabaseManager {
    database(MainDatabase) {
        config = mainConfig
    }
    database(AnalyticsDatabase("eu-west")) {
        config = analyticsConfig
    }
}

try {
    val main = manager[MainDatabase]
    val analytics = manager[AnalyticsDatabase("eu-west")]

    // عملیات هر پایگاه داده را از Handle همان Registration اجرا کنید.
} finally {
    manager.shutdown()
}
```

`mainConfig` و `analyticsConfig` نمونه‌هایی از `DatabaseConfig` برنامهٔ شما هستند؛ شکل ساخت آن‌ها در [راهنمای تنظیمات اتصال](documents/guides/database-manager/database_define/database-configuration.md) آمده است. گرفتن Handle به‌تنهایی Connection باز نمی‌کند؛ Context هر Registration هنگام نیاز ساخته می‌شود.

برای Migration، ماژول `:data_base:migration` را هم اضافه و `MigrationConfiguration` را به Registration مربوط متصل کنید:

```kotlin
dependencies {
    implementation(project(":data_base:migration"))
}
```

مراحل تعریف Migration و راه‌اندازی آن در [راهنمای ثبت Migration در Registration](documents/guides/database-manager/database_define/migrations.md) آمده است. نمونهٔ قابل اجرا با دو Registration و تنظیمات opt-in در [Example](example/README.md) قرار دارد.

<a id="common-workflows"></a>
## مسیرهای رایج

### اجرای Query

Query را با Query Builder بسازید و از Handle همان Registration اجرا کنید. برای نمونه‌های SELECT، INSERT، UPDATE، DELETE و مدیریت `ExecuteResult` از [راهنمای Query Executor](documents/guides/query-builder/executor/README.md) استفاده کنید.

### Connection و Transaction

برای یک یا چند Query با Connection مدیریت‌شده از `connection { ... }` استفاده کنید؛ برای عملیاتی که باید در یک Transaction اجرا شوند، `transaction { ... }` را به‌کار ببرید. Operation مالک lifecycle و cleanup است. جزئیات در [راهنمای Handle](documents/guides/database-manager/executor/README.md) و [راهنمای Operation](documents/guides/operation/connection_transaction/README.md) آمده است.

### Migration

Migrationها را در Configuration ثبت و به Registration متصل کنید، سپس از `handle.migration { ... }` استفاده کنید. راهنماهای [تعریف Migration و Tag](documents/guides/migration/migration_define/README.md)، [Version و گروه‌بندی](documents/guides/migration/version_define/README.md)، [اجرای فرمان‌ها](documents/guides/migration/executor/README.md) و [Statusهای پایدار](documents/guides/migration/states/README.md) مسیر کامل را توضیح می‌دهند.

<a id="usage-notes"></a>
## نکته‌های مهم استفاده

- برای کار با چند پایگاه داده، برای هر مقصد Registration و کلید مشخص تعریف کنید و عملیات را از Handle همان Registration انجام دهید.
- Manager lease از عمر عملیات پذیرفته‌شده در برابر `shutdown()` محافظت می‌کند؛ قفل سراسری برای همهٔ Queryها یا همهٔ Registrationها ایجاد نمی‌کند.
- Migrationهای هماهنگ‌شده با یک `DatabaseIdentity` از `SystemLock.MIGRATION` استفاده می‌کنند. این قفل جایگزین Transaction برنامه یا قفل‌های دیتابیس برای Queryهای عادی نیست.
- Failure را به‌عنوان نتیجهٔ عملیات بررسی کنید و آن را به موفقیت یا مقدار پیش‌فرض تبدیل نکنید. `Error` نیز مانند Failure عادی پنهان نمی‌شود.
- بعضی extensionها مانند Watch، Pipelineهای Handle و Migration به Handle مدیریت‌شدهٔ `DatabaseManager` نیاز دارند؛ پیاده‌سازی سفارشی `DatabaseHandleApi` لزوماً آن قابلیت‌ها را ندارد.

قراردادهای کامل [Lifecycle، هم‌زمانی و خطا](documents/guides/limitations/lifecycle-and-errors.md) و مرز Handleهای سفارشی در راهنماهای تخصصی توضیح داده شده‌اند.

<a id="guides"></a>
## راهنماها

| موضوع | راهنمای شروع |
|---|---|
| Database Manager، کلیدها و چند پایگاه داده | [Database Manager](documents/guides/database-manager/README.md) · [تعریف پایگاه داده](documents/guides/database-manager/database_define/README.md) · [کلیدها](documents/guides/database-manager/database_keys/README.md) · [Attributeها](documents/guides/database-manager/registration_attributes/README.md) |
| اجرای عملیات با Handle | [Connection و Transaction](documents/guides/database-manager/executor/README.md) · [Connection Pipeline](documents/guides/database-manager/executor/connection_pipeline.md) · [Transaction Pipeline](documents/guides/database-manager/executor/transaction_pipeline.md) · [Watch](documents/guides/database-manager/executor/watch.md) |
| Operation | [Connection و Transaction](documents/guides/operation/connection_transaction/README.md) · [Events](documents/guides/operation/events/README.md) · [Pipelineها](documents/guides/operation/pipelines/README.md) |
| Migration | [راهنمای اصلی](documents/guides/migration/README.md) · [تعریف](documents/guides/migration/migration_define/README.md) · [Versionها](documents/guides/migration/version_define/README.md) · [Executor](documents/guides/migration/executor/README.md) · [Stateها](documents/guides/migration/states/README.md) · [ASTهای Schema](documents/guides/migration/example_ast/README.md) |
| Query Builder | [ساخت Query](documents/guides/query-builder/README.md) · [Model](documents/guides/query-builder/model/README.md) · [CTE](documents/guides/query-builder/cte/README.md) · [Executor](documents/guides/query-builder/executor/README.md) · [مثال‌های نسخه‌بندی‌شده](documents/guides/query-builder/examples/README.md) · [قواعد Metadata](documents/guides/query-builder/rules/README.md) |
| سایر | [Collection](documents/guides/collections/README.md) · [محدودیت‌ها و خطاها](documents/guides/limitations/lifecycle-and-errors.md) · [Example دو پایگاه داده](example/README.md) · [فهرست کامل مستندات](documents/README.md) |

<a id="mcp"></a>
## MCP

در حال حاضر این مخزن MCP Server یا پیکربندی MCP برای مصرف‌کننده ارائه نمی‌کند. راهنماهای Markdown از این Repository قابل مطالعه‌اند، اما دسترسی MCP برای جست‌وجوی مستندات یا اجرای Query و Migration جزو API فعلی کتابخانه نیست.
