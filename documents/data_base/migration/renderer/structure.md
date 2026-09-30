# ساختار ماژول `data_base:migration:renderer`

رندر migration برای dialect مشخص؛ پیاده‌سازی فعلی MySQL است.

## نمودار درختی

```text
data_base:migration:renderer
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── dialects
│   │   │   │   └── MySqlDialect.kt
│   │   │   ├── interfaces
│   │   │   ├── manager
│   │   │   │   └── DialectSelector.kt
│   │   │   ├── nodes
│   │   │   │   └── create_table
│   │   │   │       ├── column
│   │   │   │       │   └── MySqlMigrationColumnCapability.kt
│   │   │   │       ├── render_migration_create_table
│   │   │   │       │   └── MySqlMigrationRenderCreateTableCapability.kt
│   │   │   │       └── table
│   │   │   │           └── MySqlMigrationTableCapability.kt
│   │   │   └── Main.kt
│   │   └── resources
│   └── test
│       ├── kotlin
│       └── resources
└── build.gradle.kts
```

## توضیح کوتاه اجزای ماژول

در `CREATE TABLE` نام columnها پس از trim و با مقایسهٔ case-insensitive یکتا هستند؛ renderer این invariant را برای AST مستقیم نیز بررسی می‌کند. shorthandهای v1.15 از renderer معمولی column استفاده می‌کنند و `DATETIME NULL` بدون default یا رفتار خودکار می‌سازند.

- `build.gradle.kts`: تنظیمات Gradle، dependencyها و taskهای این ماژول.
- `src/main/kotlin`: کد اصلی Kotlin ماژول.
- `src/test` در صورت وجود: تست‌های واحد ماژول.

## Renderer مربوط به `DROP COLUMN` (v1.3)

`nodes/drop_column/MySqlMigrationDropColumnCapability.kt` AST را پیش از quoting و ساخت SQL بررسی می‌کند، سپس `ALTER TABLE ... DROP COLUMN ...` را با quoting و escaping مخصوص MySQL تولید می‌کند. چون MySQL در این grammar `DROP COLUMN IF EXISTS` ندارد، renderer در هر دو حالت `ifExists` همین SQL معتبر را می‌سازد و این لایه اتصال دیتابیس را نمی‌شناسد.
# Renderer مربوط به `RENAME COLUMN` (v1.4)

`MySqlMigrationRenameColumnCapability` در registry `MySqlDialect` ثبت شده است. AST را پیش از quoting بررسی می‌کند و `ALTER TABLE <table> RENAME COLUMN <source> TO <target>` را با escaping backtick تولید می‌کند.

# Renderer مربوط به `MODIFY COLUMN` (v1.5)

`nodes/modify_column/MySqlMigrationModifyColumnCapability.kt` مستقل از renderer CREATE/ADD COLUMN است تا `PRIMARY KEY` و metadataهای خارج از scope وارد SQL نشوند. فهرست بستهٔ فعلی typeها: `VarcharType`, `TextType`, `IntType`, `BooleanType`, `DecimalType`, `JsonType`. ترتیب SQL ثابت است: identifier، type، nullability، default در صورت وجود، سپس `AUTO_INCREMENT` در صورت true. رشته‌های default به شکل literal escape می‌شوند؛ expressionهای خام پذیرفته نمی‌شوند.

# Renderer مربوط به `CREATE INDEX` (v1.6)

`nodes/create_index/MySqlMigrationCreateIndexCapability.kt` در `MySqlDialect` registry ثبت می‌شود و `CREATE INDEX <index> ON <table> (<column>)` تولید می‌کند. هر identifier با backtick quote می‌شود و backtick داخلی دوبرابر می‌شود. از v1.12، اگر AST دارای `indexMethod` باشد، `USING BTREE` یا `USING HASH` پس از key-part افزوده می‌شود؛ در حالت پیش‌فرض SQL قبلی عیناً حفظ می‌شود. گزینه به سایر operationهای index تعمیم داده نشده و renderer نوع engine یا پیاده‌سازی فیزیکی را بررسی نمی‌کند.

# Renderer مربوط به `DROP INDEX` (v1.7)

`nodes/drop_index/MySqlMigrationDropIndexCapability.kt` SQL به‌شکل `DROP INDEX <quoted-index-name> ON <quoted-table-name>` تولید می‌کند. هر دو identifier با backtick quote می‌شوند و backtick داخلی دوبرابر می‌شود؛ `PRIMARY` هم quote عادی می‌گیرد. `IF EXISTS` یا `ALTER TABLE` syntax تولید نمی‌شود و renderer هیچ introspection انجام نمی‌دهد.

# Renderer مربوط به `CREATE UNIQUE INDEX` (v1.8)

`nodes/create_unique_index/MySqlMigrationCreateUniqueIndexCapability.kt`، `CREATE UNIQUE INDEX <index> ON <table> (<column>)` تولید می‌کند. identifierها مانند renderer v1.6 با backtick quote و backtick داخلی double می‌شوند. renderer duplicate data یا schema را بررسی نمی‌کند و `IF NOT EXISTS` نمی‌سازد.

# Renderer مربوط به `CREATE MULTI-COLUMN INDEX` (v1.9)

`nodes/create_multi_column_index/MySqlMigrationCreateMultiColumnIndexCapability.kt` خروجی `CREATE INDEX <name> ON <table> (<column-1>, <column-2>, ...)` می‌سازد. ترتیب ورودی حفظ می‌شود، هر identifier quote و escape می‌شود و SQL گزینهٔ UNIQUE یا option دیگری ندارد. metadata یا duplicate data بررسی نمی‌شود.

# Renderer مربوط به `CREATE FULLTEXT INDEX` (v1.10)

`nodes/create_full_text_index/MySqlMigrationCreateFullTextIndexCapability.kt` عبارت `CREATE FULLTEXT INDEX <name> ON <table> (<columns...>)` تولید می‌کند. حداقل یک ستون لازم است، ترتیب ورودی حفظ می‌شود و identifierها backtick quote/escape می‌شوند. Builder و Renderer duplicate identifierها را case-insensitive رد می‌کنند؛ renderer پیش از quoting AST مستقیم را نیز validate می‌کند. نوع ستون، engine، charset/collation یا partitioning از دیتابیس خوانده نمی‌شوند.

# Renderer مربوط به Foreign Key (v1.13)

Capabilityهای مستقل create/drop SQLهای `ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY ... REFERENCES ...` و `ALTER TABLE ... DROP FOREIGN KEY ...` را تولید می‌کنند. لیست ستون‌ها quote/escape و positional render می‌شود؛ actionهای تنظیم‌شده clause اختیاری می‌سازند. renderer AST ناقص، فهرست خالی/تکراری و طول نابرابر را پیش از ساخت SQL رد می‌کند و metadata دیتابیس را نمی‌خواند.

# Renderer برای table-level `CREATE TABLE` (v1.14)

`MySqlMigrationCreateTableDefinitionCapability` در registry موجود ثبت شده و typed PK/UNIQUE/INDEX/FULLTEXT/FK definitions را render می‌کند. `MySqlMigrationRenderCreateTableCapability` همچنان ستون‌ها را اول و به ترتیب قبلی می‌نویسد و سپس table definitions را به ترتیب DSL می‌افزاید. شناسه‌ها quote/escape می‌شوند؛ هیچ schema introspection یا execution preflight وجود ندارد.
