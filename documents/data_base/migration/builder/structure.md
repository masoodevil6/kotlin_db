# ساختار ماژول `data_base:migration:builder`

builderهای fluent برای تعریف جدول، ستون و create table.

## نمودار درختی

```text
data_base:migration:builder
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── ast
│   │   │   │   └── create_table
│   │   │   │       ├── column
│   │   │   │       │   └── MigrationColumnBuilder.kt
│   │   │   │       ├── render_migration_create_table
│   │   │   │       │   └── MigrationRenderCreateTableBuilder.kt
│   │   │   │       └── table
│   │   │   │           └── MigrationTableBuilder.kt
│   │   │   ├── createTable.kt
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

## DSL مربوط به `DROP COLUMN` (v1.3)

- `dropColumn.kt` نقطهٔ ورود DSL است.
- `ast/drop_column/MigrationDropColumnBuilder.kt` ورودی‌ها را trim و مقدارهای trim‌شده را در AST نگه می‌دارد و فیلد خالی یا حذف‌شده را پیش از بازگرداندن API رد می‌کند.
- DSL `ifExists()` مقدار option را در AST روشن می‌کند.
- builder فقط به API و AST وابسته است و operation را اجرا یا SQL تولید نمی‌کند.
# DSL مربوط به `RENAME COLUMN` (v1.4)

`renameColumn { tableName("users"); name("display_name"); to("full_name") }` با `MigrationRenameColumnBuilder` ساخته می‌شود. مقادیر قبل از ذخیره در AST trim می‌شوند؛ نام‌های خالی و دو نام ستون برابر رد می‌شوند.

# DSL مربوط به `MODIFY COLUMN` (v1.5)

`modifyColumn.kt` و `ast/modify_column/MigrationModifyColumnBuilder.kt` می‌سازند desired definition مستقل. table/column nameها trim می‌شوند؛ نام، type، nullability و `autoIncrement` باید صریح باشند. default فراخوانی‌نشده یعنی `NO_DEFAULT_CLAUSE` و `default(null)` یعنی `DEFAULT_NULL`. ترتیب فراخوانی setterها semantic AST یا SQL را تغییر نمی‌دهد.

# DSL مربوط به `CREATE INDEX` (v1.6)

`createIndex.kt` با `MigrationCreateIndexBuilder` سه ورودی `tableName`, `name`, `column` را می‌گیرد. Builder فقط whitespace پیرامونی را trim می‌کند، casing/محتوا را نگه می‌دارد و table/index/column خالی را پیش از بازگرداندن DSL رد می‌کند. از v1.12، گزینهٔ اختیاری `using(IndexMethod.BTREE | IndexMethod.HASH)` را روی AST ذخیره می‌کند و آن را به operationهای دیگر index تعمیم نمی‌دهد.

# DSL مربوط به `DROP INDEX` (v1.7)

`dropIndex.kt` با `MigrationDropIndexBuilder` دو ورودی `tableName` و `name` را می‌گیرد. Builder فقط whitespace پیرامونی را trim می‌کند، casing و محتوای identifier را نگه می‌دارد و ورودی خالی را رد می‌کند؛ `PRIMARY` نیز عادی پذیرفته می‌شود.

# DSL مربوط به `CREATE UNIQUE INDEX` (v1.8)

`createUniqueIndex.kt` با `MigrationCreateUniqueIndexBuilder` ورودی‌های `tableName`، `name` و یک `column` را می‌گیرد. Builder whitespace پیرامونی را trim می‌کند، casing را حفظ می‌کند و مقدار خالی را رد می‌کند. مقدار `PRIMARY` هم special-case نمی‌شود.

# DSL مربوط به `CREATE MULTI-COLUMN INDEX` (v1.9)

`createMultiColumnIndex.kt` و `MigrationCreateMultiColumnIndexBuilder` مقادیر table، نام index و حداقل دو ستون را دریافت می‌کنند. هر فراخوانی `columns(...)` فهرست قبلی را جایگزین می‌کند. Builder identifierها را trim می‌کند، casing معتبر را حفظ می‌کند و duplicate ستون را پس از trim/case-insensitive check رد می‌کند.

# DSL مربوط به `CREATE FULLTEXT INDEX` (v1.10)

`createFullTextIndex.kt` و `MigrationCreateFullTextIndexBuilder` جدول، نام index و حداقل یک ستون را می‌گیرند. هر فراخوانی `columns(...)` فهرست قبلی را جایگزین می‌کند و ترتیب ورودی حفظ می‌شود. Builder مقدارها را trim می‌کند، spelling معتبر را نگه می‌دارد و duplicateها را پس از trim با مقایسهٔ case-insensitive رد می‌کند؛ نوع ستون و engine جدول را introspect نمی‌کند.

# DSL مربوط به Foreign Key (v1.13)

`createForeignKey.kt` و `dropForeignKey.kt` دو operation مستقل می‌سازند. Builderهای مربوطه identifierها را trim می‌کنند و casing را نگه می‌دارند؛ دو فهرست ستون باید non-empty، هم‌اندازه و درون خود بدون duplicate case-insensitive باشند. هر فراخوانی فهرست را جایگزین می‌کند. schema introspection انجام نمی‌شود.

# Builderهای table-level در `CREATE TABLE` (v1.14)

`MigrationRenderCreateTableBuilder` برای PK، UNIQUE، INDEX، FULLTEXT و FK builder blockهای مستقل می‌سازد و AST typed را به ترتیب فراخوانی اضافه می‌کند. identifierها trim می‌شوند؛ blank و ستون‌های تکراری case-insensitive رد می‌شوند و spelling باقی‌مانده عیناً نگه داشته می‌شود. Builder هیچ metadata دیتابیسی نمی‌خواند.

# shorthandهای زمانی `CREATE TABLE` (v1.15)

`timestamps()` و `softDeletes()` مستقیماً column ASTهای معمولی می‌سازند؛ AST یا abstraction جداگانه ندارند. ستون‌ها به‌ترتیب `created_at`, `updated_at`, و `deleted_at` از `DateTimeType()` و nullable هستند و default ندارند. نام‌های تکراری در Builder رد می‌شوند.
