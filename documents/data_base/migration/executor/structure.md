# ساختار ماژول `data_base:migration:executor`

اجرای migration رندرشده روی دیتابیس با استفاده از manager:execute.

## نمودار درختی

```text
data_base:migration:executor
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   └── IMigrationExecutor.kt
│   │   │   ├── manager
│   │   │   │   ├── MigrationExecutor.kt
│   │   │   │   └── MySqlCreateTableColumnDefinitionParser.kt
│   │   │   └── Main.kt
│   │   └── resources
│   └── test
│       ├── kotlin
│       └── resources
└── build.gradle.kts
```

## توضیح کوتاه اجزای ماژول

- `build.gradle.kts`: تنظیمات Gradle، dependencyها و taskهای این ماژول.
- `src/main/kotlin`: کد اصلی Kotlin ماژول.
- `src/test` در صورت وجود: تست‌های واحد ماژول.

## اجرای `DROP COLUMN` (v1.3)

`MigrationExecutor` overload مربوط به `IMigrationDropColumnApi` را به `IQueryExecute.executeTable` موجود واگذار می‌کند. با `ifExists=true` ابتدا وجود جدول و ستون در `INFORMATION_SCHEMA.COLUMNS` بررسی می‌شود؛ اگر ستون غایب باشد DDL اجرا نمی‌شود و موفقیت بی‌اثر گزارش می‌شود. اگر ستون حاضر باشد، DDL اجرا می‌شود؛ خطاهای 1091 و 1146 فقط به‌عنوان fallback برای race زمانی مهار می‌شوند و سایر خطاها حفظ می‌شوند. تست executor، خود `IQueryExecute` را در مرز موجود fake می‌کند و برای operation abstraction اجرایی جدیدی نمی‌سازد.

## اجرای `RENAME COLUMN` (v1.4)

`MigrationExecutor` اطلاعات product/version واقعی را از API metadata در `IQueryExecute` موجود دریافت می‌کند. MySQL 8.0+ و MariaDB 10.5.3+ مجاز به اجرای SQL بومی `RENAME COLUMN` هستند. اگر target مسیر native را انتخاب کند و سرور آن را پشتیبانی نکند، DDL پیش از اجرا رد می‌شود؛ executor آن را پس از خطای capability به syntax دیگری rewrite نمی‌کند. برای target قدیمی‌تر، مسیر `CHANGE COLUMN` و resolve کردن definition طبق v1.4.2 استفاده می‌شود. product mismatch، metadata ناموجود یا شکست detection پیش از DDL خطا می‌دهد. `targetDatabaseVersion` فقط برای تعیین SQL generation است و جایگزین server info واقعی نیست.

## سازگاری نسخه‌ای `RENAME COLUMN` (v1.4.2)

`targetDatabaseVersion` strategy تولید را انتخاب می‌کند. برای target قدیمی، executor ابتدا `DatabaseServerInfo` را می‌گیرد و strategy انتخاب‌شده را با سرور واقعی می‌سنجد؛ سپس از مسیر `IQueryExecute.executeSelect` خروجی `SHOW CREATE TABLE` را می‌خواند. `MySqlCreateTableColumnDefinitionParser` fragment اصلی definition ستون مبدأ را استخراج می‌کند و renderer فقط identifier مبدأ را با مقصد جایگزین می‌کند تا `CHANGE COLUMN` بسازد. اگر target مسیر native را انتخاب کرده باشد ولی سرور native را پشتیبانی نکند، پیش از `SHOW CREATE TABLE` و DDL خطا می‌دهد و fallback انجام نمی‌شود. API و AST همچنان فقط table/source/target را نگه می‌دارند.

## اجرای `MODIFY COLUMN` (v1.5)

`IMigrationExecutor` entry point این operation را دارد و `MigrationExecutor` SQL رندرشده را از مسیر موجود `IQueryExecute.executeTable` اجرا می‌کند. برای این operation introspection یا rewrite در executor انجام نمی‌شود.

## اجرای `CREATE INDEX` (v1.6)

`MigrationExecutor` ورودی `IMigrationCreateIndexApi` را با `executeOut` render می‌کند و `BuiltQuery` را از طریق `IQueryExecute.executeTable` موجود ارسال می‌کند. preflight schema یا suppression خطا انجام نمی‌شود؛ failure همان manager/database به caller بازگردانده می‌شود.
# اجرای `RENAME COLUMN` (v1.4)

ورودی operation در `MigrationExecutor` به مسیر `executeOut` و سپس `IQueryExecute.executeTable` موجود واگذار می‌شود؛ علت خطا حفظ می‌شود و executor مستقلی برای آن ساخته نشده است.

## اجرای `DROP INDEX` (v1.7)

`IMigrationExecutor` و `MigrationExecutor` ورودی `IMigrationDropIndexApi` را به `executeOut` موجود می‌سپارند و SQL را از `IQueryExecute.executeTable` اجرا می‌کنند. مسیر، success/failure manager را حفظ می‌کند و برای نبود index introspection یا fallback ندارد.

## اجرای `CREATE UNIQUE INDEX` (v1.8)

`IMigrationExecutor` و `MigrationExecutor` ورودی مستقل این operation را از مسیر `executeOut` به `IQueryExecute.executeTable` موجود می‌فرستند. خطای دیتابیس، از جمله خطای duplicate، بدون تبدیل به success حفظ می‌شود.

## اجرای `CREATE MULTI-COLUMN INDEX` (v1.9)

`IMigrationExecutor` و `MigrationExecutor` ورودی API جدید را از `executeOut` به `IQueryExecute.executeTable` موجود می‌سپارند؛ preflight select وجود ندارد و failure دیتابیس به callback می‌رسد.

## اجرای `CREATE FULLTEXT INDEX` (v1.10)

ورودی مستقل `IMigrationCreateFullTextIndexApi` از مسیر `executeOut` به `IQueryExecute.executeTable` موجود می‌رود. Executor قبل از DDL برای schema introspection یا preflight سراغ `executeSelect` نمی‌رود؛ خطاهای MySQL به callback می‌رسند.
