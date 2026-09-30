# ساختار ماژول `data_base:migration:example`

نمونه‌های نسخه‌بندی‌شده برای API و builderهای migration. اجرای واقعی از طریق `MigrationExecutor` انجام می‌شود و به manager دیتابیس واگذار می‌گردد. مثال `DROP COLUMN` به‌صورت پیش‌فرض render می‌شود و فقط وقتی `statusExecute` صریحاً true باشد اجرا می‌شود.

## نمودار درختی

```text
data_base:migration:example
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── managers
│   │   │   │   ├── IManagerExample.kt
│   │   │   │   └── ManagerExampleV1.kt
│   │   │   ├── v1
│   │   │   │   └── migrations
│   │   │   │       ├── create_table
│   │   │   │       │   └── A1ExampleCreateTableV1.kt
│   │   │   │       ├── create_table_timestamps
│   │   │   │       │   └── A1ExampleCreateTableTimestampsV1.kt
│   │   │   │       └── IExampleV1.kt
│   │   │   └── Main.kt
│   │   └── resources
│   └── test
│       ├── kotlin
│       │   └── v1
│       │       └── migrations
│       │           └── create_table
│       │               └── A1ExampleCreateTableV1Test.kt
│       └── resources
└── build.gradle.kts
```

## توضیح کوتاه اجزای ماژول

- `build.gradle.kts`: تنظیمات Gradle، dependencyها و taskهای این ماژول.
- `src/main/kotlin`: کد اصلی Kotlin ماژول.
- `src/test` در صورت وجود: تست‌های واحد ماژول.

برای اجرای مثال‌ها، از جمله render کردن `DROP COLUMN`:

```text
./gradlew :data_base:migration:example:run
```

برای اجرای تست‌های render و validation:

```text
./gradlew :data_base:migration:example:test
```

`Main.kt` dialect و target version را در `DatabaseConfig` تنظیم می‌کند (نمونهٔ فعلی MariaDB 10.4.28). این نسخه target، مسیر `CHANGE COLUMN` را انتخاب می‌کند. در اجرای rename، مثال به executor واگذار می‌شود تا definition ستون را از دیتابیس resolve کند؛ direct render به‌تنهایی برای target قدیمی تعریف کافی ندارد. تست‌های واحد به اتصال دیتابیس نیاز ندارند.

## مثال `MODIFY COLUMN` (v1.5)

`v1/migrations/modify_column/A1ExampleModifyColumnV1.kt` SQL را برای `users.name` render می‌کند. اجرای DDL فقط با فعال‌کردن هم‌زمان `statusRunModifyColumn` و `statusExecute` انجام می‌شود؛ هر دو در مثال اصلی خاموش‌اند.

## مثال `CREATE INDEX` (v1.6)

`v1/migrations/create_index/A1ExampleCreateIndexV1.kt` SQL مربوط به index تک‌ستونی `idx_users_email` را render می‌کند. اجرای آن با `statusRunCreateIndex` کنترل می‌شود و این گزینه در `Main.kt` به‌صورت پیش‌فرض خاموش است.

فایل مثال: `src/main/kotlin/v1/migrations/drop_column/A1ExampleDropColumnV1.kt`. تست آن در `src/test/kotlin/v1/migrations/drop_column/A1ExampleDropColumnV1Test.kt` قرار دارد.
# مثال `RENAME COLUMN` (v1.4)

`v1/migrations/rename_column/A1ExampleRenameColumnV1.kt` مثال نسخه‌بندی‌شده را نگه می‌دارد. هنگام اجرای operation، SQL نهایی در callback query اطلاعات چاپ می‌شود؛ target legacy پیش از compose نهایی، definition اصلی ستون را از manager می‌گیرد.

## مثال `DROP INDEX` (v1.7)

`v1/migrations/drop_index/A1ExampleDropIndexV1.kt` SQL حذف `idx_users_email` را render می‌کند. اجرای آن با `statusRunDropIndex` کنترل می‌شود و این گزینه به‌صورت پیش‌فرض خاموش است.

## مثال `CREATE UNIQUE INDEX` (v1.8)

`v1/migrations/create_unique_index/A1ExampleCreateUniqueIndexV1.kt` خروجی unique index روی `users.email` را render می‌کند. `statusRunCreateUniqueIndex` در manager به‌طور پیش‌فرض false است؛ مثال فقط با opt-in اجرا می‌شود.

## مثال `CREATE MULTI-COLUMN INDEX` (v1.9)

`v1/migrations/create_multi_column_index/A1ExampleCreateMultiColumnIndexV1.kt` index عادی سه‌ستونی را render می‌کند. اجرای مثال با `statusRunCreateMultiColumnIndex` کنترل می‌شود و این گزینه در manager به‌صورت پیش‌فرض خاموش است.

## مثال `CREATE FULLTEXT INDEX` (v1.10)

`v1/migrations/create_full_text_index/A1ExampleCreateFullTextIndexV1.kt` FULLTEXT index روی `posts(title, body)` را render می‌کند. `statusRunCreateFullTextIndex` در `ManagerExampleV1` پیش‌فرض false دارد؛ اجرای DDL علاوه بر فعال‌کردن این گزینه، نیازمند `statusExecute=true` است. پیش از اجرای واقعی، جدول و نوع/engine ستون‌ها باید با محدودیت‌های MySQL سازگار باشند.

## مثال table-level definitions در `CREATE TABLE` (v1.14)

`v1/migrations/create_table_constraints/A1ExampleCreateTableConstraintsV1.kt` جدول `orders` را با composite primary key، UNIQUE، index چندستونی، FULLTEXT و foreign key تعریف می‌کند. کلاس در `ManagerExampleV1` ثبت شده و با `statusRunCreateTable` قابل render است؛ اجرای DDL همچنان به opt-in موجود `statusExecute` وابسته است. `Main.kt` تغییر نکرده است.

## مثال `timestamps()` و `softDeletes()` در `CREATE TABLE` (v1.15)

`v1/migrations/create_table_timestamps/A1ExampleCreateTableTimestampsV1.kt` جدول `posts` را با `created_at`, `updated_at` و `deleted_at` از نوع `DATETIME NULL` می‌سازد. مثال در `ManagerExampleV1` ثبت شده؛ render پیش‌فرض حفظ می‌شود و اجرای واقعی همچنان نیازمند opt-in صریح `statusExecute` است.
