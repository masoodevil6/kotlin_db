# ساختار ماژول `data_base:migration:dialect`

مدل‌ها و قراردادهای رندر migration مستقل از موتور دیتابیس.

## نمودار درختی

```text
data_base:migration:dialect
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── data_class
│   │   │   │   ├── create_table
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── MigrationColumnData.kt
│   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   └── MigrationRenderCreateTableData.kt
│   │   │   │   │   └── table
│   │   │   │   │       └── MigrationTableData.kt
│   │   │   │   └── MigrationDataClass.kt
│   │   │   ├── interfaces
│   │   │   │   ├── IAstRenderer.kt
│   │   │   │   ├── IRenderContext.kt
│   │   │   │   ├── IRendererRegistry.kt
│   │   │   │   └── ISqlDialect.kt
│   │   │   ├── manager
│   │   │   │   ├── BaseSqlDialect.kt
│   │   │   │   ├── RenderContext.kt
│   │   │   │   └── RendererRegistry.kt
│   │   │   ├── nodes
│   │   │   │   ├── rename_column
│   │   │   │   │   └── RenameColumnSqlStrategy.kt
│   │   │   │   └── create_table
│   │   │   │       ├── column
│   │   │   │       │   └── IMigrationColumnCapability.kt
│   │   │   │       ├── render_migration_create_table
│   │   │   │       │   └── IMigrationRenderCreateTableCapability.kt
│   │   │   │       └── table
│   │   │   │           └── IMigrationTableCapability.kt
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

## قرارداد dialect برای `DROP COLUMN` (v1.3)

`nodes/drop_column/IMigrationDropColumnCapability.kt` مرز renderer operation را تعریف می‌کند. این قرارداد به connection، executor یا JDBC وابسته نیست.
# قرارداد `RENAME COLUMN` (v1.4)

`IMigrationRenameColumnCapability` rendering این operation را در dialect تعریف می‌کند؛ API، AST و dialect از اتصال دیتابیس مستقل هستند.

## قرارداد سازگاری `RENAME COLUMN` (v1.4.2)

`IRenameColumnSqlDialect` یک handoff محدود و operation-specific برای strategy و rendering مسیر legacy فراهم می‌کند. `targetDatabaseVersion` انتخاب می‌کند که SQL بومی `RENAME COLUMN` باشد یا executor پس از resolve کردن definition ستون، `CHANGE COLUMN` را درخواست کند. renderer هیچ دسترسی به manager یا اتصال ندارد.

# قرارداد dialect برای `MODIFY COLUMN` (v1.5)

`nodes/modify_column/IMigrationModifyColumnCapability.kt` مرز render operation را تعریف می‌کند و فقط AST desired definition را دریافت می‌کند؛ existing column metadata، manager و JDBC در دسترس renderer نیست.

# قرارداد dialect برای `CREATE INDEX` (v1.6)

`nodes/create_index/IMigrationCreateIndexCapability.kt` capability اختصاصی AST این operation است. Renderer registry موجود capability را با AST interface ثبت می‌کند؛ قرارداد به اتصال یا executor وابسته نیست.

# قرارداد dialect برای `DROP INDEX` (v1.7)

`nodes/drop_index/IMigrationDropIndexCapability.kt` capability اختصاصی AST است. `MySqlDialect` آن را در registry موجود ثبت می‌کند؛ این قرارداد به connection یا executor وابسته نیست.

# قرارداد dialect برای `CREATE UNIQUE INDEX` (v1.8)

`nodes/create_unique_index/IMigrationCreateUniqueIndexCapability.kt` capability مجزای AST این operation است و در registry موجود `MySqlDialect` ثبت می‌شود. قرارداد dialect مستقل از manager، connection و JDBC می‌ماند.

# قرارداد dialect برای `CREATE MULTI-COLUMN INDEX` (v1.9)

`nodes/create_multi_column_index/IMigrationCreateMultiColumnIndexCapability.kt` capability مستقل AST است که در registry موجود `MySqlDialect` ثبت می‌شود. این قرارداد از connection و executor مستقل است.

# قرارداد dialect برای `CREATE FULLTEXT INDEX` (v1.10)

`nodes/create_full_text_index/IMigrationCreateFullTextIndexCapability.kt` capability مستقل AST را تعریف می‌کند و در registry موجود `MySqlDialect` ثبت می‌شود. این لایه به connection، manager یا executor وابستگی ندارد.

# قرارداد dialect برای Foreign Key (v1.13)

`nodes/create_foreign_key/` و `nodes/drop_foreign_key/` دو capability مستقل تعریف می‌کنند. ASTها در registry موجود `MySqlDialect` ثبت شده‌اند؛ `MariaDbDialect` همان registration ارث‌رسیده را مصرف می‌کند. این لایه به connection یا executor وابسته نیست.

# قرارداد dialect برای table-level `CREATE TABLE` (v1.14)

table-level definitionهای typed از مسیر registry موجود `MySqlDialect` به renderer واحد و محدود به CREATE TABLE می‌رسند. capabilityهای standalone v1.6–v1.13 تغییر نمی‌کنند. `MariaDbDialect` registration ارث‌رسیده را حفظ می‌کند؛ CHECK و SPATIAL در این contract نیستند.
