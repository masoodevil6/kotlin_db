# ساختار ماژول `data_base:migration:api`

قراردادهای عمومی migration برای جدول، ستون و رندر ایجاد جدول.

## نمودار درختی

```text
data_base:migration:api
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   ├── create_table
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── IMigrationColumnApi.kt
│   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   └── IMigrationRenderCreateTableApi.kt
│   │   │   │   │   └── table
│   │   │   │   │       └── IMigrationTableApi.kt
│   │   │   │   └── IMigrationApi.kt
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

## قرارداد `DROP COLUMN` (v1.3)

API این operation در `interfaces/drop_column/IMigrationDropColumnApi.kt` قرار دارد و ورودی‌های `tableName(...)`، `name(...)` و option صریح `ifExists()` را تعریف می‌کند. این قرارداد مستقل از execution است.
# `RENAME COLUMN` (v1.4)

`IMigrationRenameColumnApi` در `interfaces/rename_column/` ورودی‌های `tableName`، `name` و `to` را تعریف می‌کند. این operation تعریف ستون را تغییر نمی‌دهد.

# `MODIFY COLUMN` (v1.5)

`interfaces/modify_column/IMigrationModifyColumnApi.kt` DSL مستقل full desired definition را ارائه می‌کند: جدول، نام ستون، type، nullability، default و state صریح `AUTO_INCREMENT`. این API operation را اجرا نمی‌کند.

# `CREATE INDEX` (v1.6)

`interfaces/create_index/IMigrationCreateIndexApi.kt` قرارداد `tableName(...)`، `name(...)`، `column(...)` و در v1.12 گزینهٔ اختیاری `using(IndexMethod)` را ارائه می‌کند. `IndexMethod` فقط `BTREE` و `HASH` دارد و فقط برای `createIndex` معمولی است؛ APIهای unique، multi-column، full-text و spatial تغییر نمی‌کنند.

# `DROP INDEX` (v1.7)

`interfaces/drop_index/IMigrationDropIndexApi.kt` قرارداد مستقل `tableName(...)` و `name(...)` را ارائه می‌کند. `name()` شناسهٔ index است؛ `PRIMARY` هم بدون API یا رفتار ویژه در Builder پذیرفته می‌شود.

# `CREATE UNIQUE INDEX` (v1.8)

`interfaces/create_unique_index/IMigrationCreateUniqueIndexApi.kt` یک API مستقل با `tableName(...)`، `name(...)` و `column(...)` دارد. یک ستون دریافت می‌شود و unique بودن به شکل flag به API `createIndex` اضافه نمی‌شود.

# `CREATE MULTI-COLUMN INDEX` (v1.9)

`interfaces/create_multi_column_index/IMigrationCreateMultiColumnIndexApi.kt` قرارداد مستقلی با `tableName(...)`، `name(...)` و `columns(vararg ...)` ارائه می‌کند. APIهای `createIndex` و `createUniqueIndex` تغییر نمی‌کنند.

# `CREATE FULLTEXT INDEX` (v1.10)

`interfaces/create_full_text_index/IMigrationCreateFullTextIndexApi.kt` API مستقل `tableName(...)`، `name(...)` و `columns(vararg ...)` را تعریف می‌کند. این operation معنای FULLTEXT را دارد و به APIهای index عادی flag اضافه نمی‌کند.

# `FOREIGN KEY` (v1.13)

`interfaces/create_foreign_key/IMigrationCreateForeignKeyApi.kt` برای افزودن constraint به جدول موجود، جدول، نام constraint، دو فهرست ordered از ستون‌های محلی و مرجع، جدول مرجع و actionهای اختیاری را می‌گیرد. `IMigrationDropForeignKeyApi` فقط جدول و نام constraint را دریافت می‌کند. هر دو API مستقل از execution هستند.

# APIهای table-level در `CREATE TABLE` (v1.14)

`IMigrationRenderCreateTableApi` blockهای `primaryKey`, `unique`, `index`, `fullTextIndex` و `foreignKey` را اضافه می‌کند. هر block API مستقل و کوچک خود را دارد؛ `columns(...)` فهرست قبلی را جایگزین می‌کند. APIهای v1.6 تا v1.13 تغییر نکرده‌اند.

# shorthandهای زمانی `CREATE TABLE` (v1.15)

`IMigrationRenderCreateTableApi` دو متد بدون آرگومان `timestamps()` و `softDeletes()` دارد. این‌ها فقط تعریف schema می‌سازند: اولی `created_at` و `updated_at` و دومی `deleted_at` را اضافه می‌کند. رفتار ORM یا lifecycle در API وجود ندارد.
