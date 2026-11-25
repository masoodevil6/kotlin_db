# آماده‌سازی Migration با `init()`

`init()` جداول داخلی موردنیاز Migration را در Operation Scope فراخواننده آماده می‌کند. این فرمان Migrationهای ثبت‌شده در Configuration را اجرا نمی‌کند و `up()` یا `down()` را فراخوانی نمی‌کند.

## استفاده از API

روش معمول، اجرای فرمان از طریق Migration facade متصل به Database Handle است:

```kotlin
val initialized: Boolean = database.migration {
    init()
}.toValue().toValue()
```

`database.migration { ... }` نتیجهٔ Operation را برمی‌گرداند و مقدار `init()` نتیجهٔ داخلی فرمان است؛ این دو نتیجه به‌صورت خودکار در یکدیگر ادغام نمی‌شوند. `toValue()` روی هر لایه، مقدار موفق را برمی‌گرداند و در صورت Failure همان Exception را پرتاب می‌کند. اگر لازم است Failure را بدون پرتاب بررسی کنید، هر دو لایه را جداگانه بررسی کنید:

```kotlin
val operation: ExecuteResult<ExecuteResult<Boolean>> = database.migration {
    init()
}

when (operation) {
    is ExecuteResult.Failure -> println("Operation failed: ${operation.exception}")
    is ExecuteResult.Success -> when (val command = operation.result) {
        null -> println("Operation returned no command result")
        is ExecuteResult.Failure -> println("Migration initialization failed: ${command.exception}")
        is ExecuteResult.Success -> when (command.result) {
            true -> println("Migration storage is ready")
            false, null -> println("Migration initialization did not complete")
        }
    }
}
```

برای این API، `MigrationFacade.init()` نتیجهٔ فرمان را از `MigrationController.init(scope)` می‌گیرد. Controller نیز همان Scope دریافتی را به `SystemMigrationInitializer` می‌دهد؛ initializer مالک Connection یا چرخهٔ عمر Operation نیست.

## چه چیزی آماده می‌شود؟

ترتیب اجرا در initializer چنین است:

1. ساخت جدول `system_migration_tag` در صورت نبودن آن.
2. ساخت جدول `system_migration_version` در صورت نبودن آن.
3. بررسی وجود ستون nullable به نام `retry_checkpoint` در `system_migration_version` و افزودن آن در صورت نیاز.
4. ساخت جدول `system_migration` در صورت نبودن آن.

تعریف جدول‌ها از Schemaهای داخلی Migration می‌آید. تعریف‌های ساخت جدول از `IF NOT EXISTS` استفاده می‌کنند. برای جدول Version، تعریف تازه شامل ستون `retry_checkpoint` است؛ برای نصب موجودی که این ستون را ندارد، initializer وجود ستون را در `information_schema.COLUMNS` بررسی می‌کند و در صورت نیاز دستور زیر را اجرا می‌کند:

```sql
ALTER TABLE `system_migration_version` ADD COLUMN `retry_checkpoint` TEXT NULL
```

بررسی ستون یک ردیف با aliasهای `selected_schema` و `column_count` انتظار دارد. نام دیتابیس باید موجود و غیرخالی باشد. نتیجهٔ probe ناقص، چندردیفی یا نامعتبر به‌عنوان شکست پردازش می‌شود.

این آماده‌سازی، بررسی جامع Schema جداول موجود نیست؛ `IF NOT EXISTS` ساختار جدول موجود را با تمام تعریف‌های فعلی مقایسه یا اصلاح نمی‌کند. تنها ارتقای ستونی که initializer به‌طور مشخص انجام می‌دهد `retry_checkpoint` است.

## قفل در ارتقای ستون

اگر probe اولیه ستون `retry_checkpoint` را پیدا کند، initializer برای این بررسی قفل Migration نمی‌گیرد. اگر ستون موجود نباشد، از `SystemLock.MIGRATION` استفاده می‌کند، داخل callback قفل وجود ستون را دوباره بررسی می‌کند و فقط در صورت نبودن آن `ALTER TABLE` را اجرا می‌کند. قفل تا پایان callback نگه داشته می‌شود؛ این سازوکار اجرای هم‌زمان همین ارتقا را هماهنگ می‌کند.

در مسیر نیازمند ارتقا، Scope باید بتواند از قرارداد موجود `withSystemLock()` استفاده کند: Scope فعال و managed از نوع root Connection، دارای `DatabaseIdentity` canonical و dialect پشتیبانی‌شدهٔ MySQL یا MariaDB، بدون advisory lock فعال دیگری روی همان Scope. اگر این پیش‌نیازها برقرار نباشند یا گرفتن قفل/عملیات داخل آن شکست بخورد، initializer با Failure متوقف می‌شود و مسیر بدون قفل را ادامه نمی‌دهد.

توجه کنید که آماده‌سازی Tag و Version و probe اولیه پیش از گرفتن قفل انجام می‌شوند. بنابراین قفل Migration تمام مراحل `init()` را در بر نمی‌گیرد؛ فقط بررسی مجدد و افزودن ستون را پوشش می‌دهد. اگر probe نشان دهد ستون از قبل وجود دارد، initializer می‌تواند این ارتقای قفل‌شده را رد کند. این رفتار به معنی پشتیبانی از هر Scope برای کل سایر فرمان‌های Migration نیست.

## نتیجه و مدیریت خطا

در صورت تکمیل همهٔ مراحل، نتیجهٔ فرمان `ExecuteResult.Success(true)` است. با شکست یک مرحله، مراحل بعدی اجرا نمی‌شوند و Failure همان مسیر به caller برمی‌گردد. اگر Exception هنگام اجرای initializer رخ دهد، در Failure مربوط به Operation ثبت می‌شود؛ خطاهای fatal از نوع `Error` ثبت و با همان مسیر fatal دوباره پرتاب می‌شوند.

Initializer فقط از Scope و Connection موجود استفاده می‌کند. خودش `autoCommit` را تغییر نمی‌دهد، `commit()` یا `rollback()` انجام نمی‌دهد و Connection را نمی‌بندد. مالک Operation مسئول این lifecycle است. DDLهای متوالی نیز در این API به‌عنوان یک عملیات اتمیک یا قابل rollback تضمین نمی‌شوند؛ در شکست میانی ممکن است بخشی از جداول از قبل ایجاد شده باشد. اجرای مجدد با همان دیتابیس، تعریف‌های `IF NOT EXISTS` را برای جدول‌ها به کار می‌گیرد و probe ستون را دوباره انجام می‌دهد.

`init()` را پیش از فرمان‌هایی اجرا کنید که به جداول داخلی Migration نیاز دارند. این فرمان فقط storage داخلی را آماده می‌کند؛ شروع اجرای Migrationهای Configuration بر عهدهٔ [راهنمای `migrate()`](../migrate/README.md) است.

## مسیرهای Source

- Facade مصرف‌کننده: `data_base/migration/src/main/kotlin/facade/MigrationFacade.kt`
- اتصال Database Handle به Operation Scope: `data_base/migration/src/main/kotlin/facade/DatabaseHandleMigration.kt`
- API Controller: `data_base/migration/executor/src/main/kotlin/controller/MigrationController.kt`
- ترتیب ساخت جدول‌ها و ارتقای ستون: `data_base/migration/executor/src/main/kotlin/service/init/SystemMigrationInitializer.kt`
- تعریف جدول‌های داخلی: `data_base/migration/core/src/main/kotlin/system_migration/schema/`
