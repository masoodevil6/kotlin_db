# ساختار ماژول `data_base:migration`

ماژول والد زیرسیستم migration و ساخت schema.

## نمودار درختی

```text
data_base:migration
├── api
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── interfaces
│   │   │   │   │   ├── create_table
│   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   └── IMigrationColumnApi.kt
│   │   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   │   └── IMigrationRenderCreateTableApi.kt
│   │   │   │   │   │   └── table
│   │   │   │   │   │       └── IMigrationTableApi.kt
│   │   │   │   │   └── IMigrationApi.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── ast
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── interfaces
│   │   │   │   │   ├── create_table
│   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   └── IMigrationColumnAst.kt
│   │   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   │   └── IMigrationRenderCreateTableAst.kt
│   │   │   │   │   │   └── table
│   │   │   │   │   │       └── IMigrationTableAst.kt
│   │   │   │   │   └── IMigrationAst.kt
│   │   │   │   ├── schema
│   │   │   │   │   └── create_table
│   │   │   │   │       ├── column
│   │   │   │   │       │   └── MigrationColumnAst.kt
│   │   │   │   │       ├── render_query_create_table
│   │   │   │   │       │   └── MigrationRenderCreateTableAst.kt
│   │   │   │   │       └── table
│   │   │   │   │           └── MigrationTableAst.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── builder
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── ast
│   │   │   │   │   └── create_table
│   │   │   │   │       ├── column
│   │   │   │   │       │   └── MigrationColumnBuilder.kt
│   │   │   │   │       ├── render_migration_create_table
│   │   │   │   │       │   └── MigrationRenderCreateTableBuilder.kt
│   │   │   │   │       └── table
│   │   │   │   │           └── MigrationTableBuilder.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── dialect
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── data_class
│   │   │   │   │   ├── create_table
│   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   └── MigrationColumnData.kt
│   │   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   │   └── MigrationRenderCreateTableData.kt
│   │   │   │   │   │   └── table
│   │   │   │   │   │       └── MigrationTableData.kt
│   │   │   │   │   └── MigrationDataClass.kt
│   │   │   │   ├── interfaces
│   │   │   │   │   ├── IAstRenderer.kt
│   │   │   │   │   ├── IRenderContext.kt
│   │   │   │   │   ├── IRendererRegistry.kt
│   │   │   │   │   └── ISqlDialect.kt
│   │   │   │   ├── manager
│   │   │   │   │   ├── BaseSqlDialect.kt
│   │   │   │   │   ├── RenderContext.kt
│   │   │   │   │   └── RendererRegistry.kt
│   │   │   │   ├── nodes
│   │   │   │   │   └── create_table
│   │   │   │   │       ├── column
│   │   │   │   │       │   └── IMigrationColumnCapability.kt
│   │   │   │   │       ├── render_migration_create_table
│   │   │   │   │       │   └── IMigrationRenderCreateTableCapability.kt
│   │   │   │   │       └── table
│   │   │   │   │           └── IMigrationTableCapability.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── example
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── executor
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── interfaces
│   │   │   │   │   └── IMigrationExecutor.kt
│   │   │   │   ├── manager
│   │   │   │   │   └── MigrationExecutor.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── params
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── data_types
│   │   │   │   │   ├── BooleanType.kt
│   │   │   │   │   ├── DecimalType.kt
│   │   │   │   │   ├── IntType.kt
│   │   │   │   │   ├── JsonType.kt
│   │   │   │   │   ├── MigrationColumnDataType.kt
│   │   │   │   │   ├── TextType.kt
│   │   │   │   │   └── VarcharType.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── renderer
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── dialects
│   │   │   │   │   └── MySqlDialect.kt
│   │   │   │   ├── interfaces
│   │   │   │   ├── manager
│   │   │   │   │   └── DialectSelector.kt
│   │   │   │   ├── nodes
│   │   │   │   │   └── create_table
│   │   │   │   │       ├── column
│   │   │   │   │       │   └── MySqlMigrationColumnCapability.kt
│   │   │   │   │       ├── render_migration_create_table
│   │   │   │   │       │   └── MySqlMigrationRenderCreateTableCapability.kt
│   │   │   │   │       └── table
│   │   │   │   │           └── MySqlMigrationTableCapability.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── src
│   ├── main
│   │   ├── kotlin
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

## عملیات `DROP COLUMN` (v1.3)

این operation از API و AST مستقل استفاده می‌کند و `ifExists()` دارد؛ builder ورودی‌ها را trim می‌کند و MySQL renderer پیش از quoting، AST را اعتبارسنجی می‌کند. چون MySQL syntax مستقیم `DROP COLUMN IF EXISTS` ندارد، `MigrationExecutor` فقط خطاهای missing-table/missing-column مشخص را با option فعال به no-op موفق تبدیل می‌کند و اجرای آن را از طریق `IQueryExecute` موجود در `data_base:manager` انجام می‌دهد.

جهت وابستگی حفظ می‌شود: AST و builder به execution وابسته نیستند؛ dialect و renderer نیز manager یا JDBC را نمی‌شناسند. جزئیات اجرای دیتابیس در مرز executor/manager باقی می‌مانند. پلن جزئیات این قرارداد و مراحل را در `plans/migration/v1.3-drop-column-contract.md` ثبت می‌کند.
# عملیات `RENAME COLUMN` (v1.4)

این operation با API/AST مستقل، Builder، renderer و ورودی executor موجود نام یک ستون را تغییر می‌دهد. AST هیچ attribute تعریف ستون ندارد. manager محصول و نسخهٔ واقعی را از اتصال به شکل `DatabaseServerInfo` برمی‌گرداند. `DatabaseConfig.targetDatabaseVersion` strategy تولید SQL را انتخاب می‌کند: MySQL 8.0+ و MariaDB 10.5.3+ از `RENAME COLUMN` بومی استفاده می‌کنند؛ target قدیمی‌تر از مسیر resolve کردن definition ستون و `CHANGE COLUMN` استفاده می‌کند. executor ابتدا strategy را با server info واقعی اعتبارسنجی می‌کند؛ mismatch یا capability ناکافی پیش از introspection/DDL خطا می‌دهد و syntax را بر اساس نسخهٔ واقعی بازانتخاب نمی‌کند. تعریف ستون در لایهٔ executor از `SHOW CREATE TABLE` خوانده می‌شود و renderer به manager وابسته نیست.

# عملیات `MODIFY COLUMN` (v1.5)

`MODIFY COLUMN` یک replacement operation با full supported desired definition است: نام، type، nullability، default state و `AUTO_INCREMENT`. typeهای پذیرفته‌شده فعلاً فقط `VarcharType`, `TextType`, `IntType`, `BooleanType`, `DecimalType`, و `JsonType` هستند. AST و renderer آن مستقل از مدل CREATE TABLE هستند، چون renderer مشترک CREATE ستون را `PRIMARY KEY` نیز تولید می‌کند. renderer هیچ اطلاعی از schema قبلی ندارد؛ `NO_DEFAULT_CLAUSE`، `DEFAULT NULL` و مقدار default سه state جدا هستند. `PRIMARY KEY`، index و FK خارج از scope هستند. executor از manager موجود استفاده می‌کند و DDL را از `IQueryExecute.executeTable` می‌فرستد. جزئیات در `plans/migration/v1.5-modify-column-contract.md` است.

# عملیات `CREATE INDEX` (v1.6)

v1.6 یک operation مستقل برای ساخت index عادی تک‌ستونی با نام صریح است. DSL آن `createIndex { tableName(...); name(...); column(...) }` است و AST مقادیر را به شکل `tableName`, `indexName`, `columnName` نگه می‌دارد. Builder فقط whitespace پیرامونی را trim می‌کند؛ casing را تغییر نمی‌دهد. renderer MySQL با quoting/escaping موجود `CREATE INDEX ... ON ... (...)` می‌سازد. introspection، auto-naming، `UNIQUE`، چندستونی و `IF NOT EXISTS` خارج scope هستند. executor از `IQueryExecute.executeTable` موجود عبور می‌کند و خطاها را حفظ می‌کند. `DROP INDEX` operation بعدی و plan جداگانه است. جزئیات در `plans/migration/v1.6-create-index-contract.md` است.

# عملیات `DROP INDEX` (v1.7)

v1.7 یک index نام‌گذاری‌شده را از جدول مشخص حذف می‌کند: `dropIndex { tableName(...); name(...) }`. AST فقط `tableName` و `indexName` را نگه می‌دارد و MySQL renderer، `DROP INDEX <quoted-index> ON <quoted-table>` تولید می‌کند. شناسه‌ها trim می‌شوند و casing حفظ می‌شود؛ `PRIMARY` هم مانند هر نام index دیگری quote می‌شود و MySQL آن را به‌عنوان primary-key index تفسیر می‌کند. `IF EXISTS` و introspection وجود ندارند. اجرا از مسیر `MigrationExecutor` و `IQueryExecute.executeTable` است. جزئیات در `plans/migration/v1.7-drop-index-contract.md` ثبت شده است.

# عملیات `CREATE UNIQUE INDEX` (v1.8)

v1.8 یک unique index مستقل و تک‌ستونی با DSL `createUniqueIndex { tableName(...); name(...); column(...) }` ایجاد می‌کند. AST مانند CREATE INDEX عادی، سه identifier را نگه می‌دارد و uniqueness از نوع operation مشخص می‌شود. MySQL renderer خروجی `CREATE UNIQUE INDEX ... ON ... (...)` را با quoting/escaping identifierها تولید می‌کند. هیچ duplicate scan یا introspection انجام نمی‌شود؛ محدودیت uniqueness را خود دیتابیس اعمال می‌کند. executor از `IQueryExecute.executeTable` موجود استفاده می‌کند. چندستونی و تغییر API v1.6 خارج scope هستند. جزئیات در `plans/migration/v1.8-create-unique-index-contract.md` است.

# عملیات `CREATE MULTI-COLUMN INDEX` (v1.9)

v1.9 یک index عادی روی حداقل دو ستون می‌سازد: `createMultiColumnIndex { tableName(...); name(...); columns(...) }`. API و AST مستقل‌اند؛ هر فراخوانی `columns(...)` فهرست قبلی را جایگزین می‌کند و ترتیب آرگومان‌های آخرین فراخوانی تا SQL حفظ می‌شود. Builder ستون‌های تکراری را پس از trim و با مقایسهٔ case-insensitive رد می‌کند ولی spelling معتبر را در AST حفظ می‌کند. MySQL renderer همهٔ شناسه‌ها را quote/escape می‌کند. هیچ سقف مصنوعی تعداد ستون در library یا preflight وجود ندارد؛ محدودیت واقعی به دیتابیس سپرده می‌شود. executor از `IQueryExecute.executeTable` موجود استفاده می‌کند. جزئیات در `plans/migration/v1.9-create-multi-column-index-contract.md` است.

# عملیات `CREATE FULLTEXT INDEX` (v1.10)

`createFullTextIndex { tableName(...); name(...); columns(...) }` operation مستقلی با AST و MySQL renderer مستقل است. یک یا چند ستون می‌پذیرد؛ ترتیب حفظ می‌شود و duplicateها پس از trim با مقایسهٔ case-insensitive به‌عنوان invariant کتابخانه رد می‌شوند. نوع ستون، storage engine، charset/collation و partitioning introspect نمی‌شوند و محدودیت‌هایشان به MySQL واگذار می‌شود. `WITH PARSER`, search DSL و prefix length در این API نیستند. `DROP INDEX` v1.7 برای حذف آن کافی است. اجرای مثال با opt-in خاموش است؛ executor از مسیر manager و `IQueryExecute.executeTable` استفاده می‌کند. جزئیات در `plans/migration/v1.10-create-fulltext-index-contract.md` است.

# Table-level definitions در `CREATE TABLE` (v1.14)

`IMigrationRenderCreateTableApi` امکان تعریف table-level `PRIMARY KEY`، `UNIQUE`، normal `INDEX` تک‌ستونی/چندستونی با method اختیاری `BTREE`/`HASH`، `FULLTEXT INDEX` و `FOREIGN KEY` ترکیبی را اضافه می‌کند. AST این موارد را به‌صورت typed و ordered نگه می‌دارد؛ renderer بعد از ستون‌ها و به ترتیب DSL می‌نویسد. `primaryKey()` ستونی و SQL فعلی حفظ می‌شوند؛ ترکیب PK ستونی و table-level مجاز نیست.

مثال versioned: `data_base/migration/example/src/main/kotlin/v1/migrations/create_table_constraints/A1ExampleCreateTableConstraintsV1.kt`. این مثال در `ManagerExampleV1` زیر flag موجود `statusRunCreateTable` ثبت شده؛ DDL فقط وقتی `statusExecute` نیز صریحاً فعال باشد اجرا می‌شود. CHECK، SPATIAL و spatial data types/SRID خارج scope هستند. اجرا از مسیر `MigrationExecutor` و `IQueryExecute.executeTable` موجود می‌گذرد و introspection یا preflight اضافه نمی‌کند.

# shorthandهای زمانی `CREATE TABLE` (v1.15)

`timestamps()` ستون‌های `created_at` و `updated_at` را می‌سازد و `softDeletes()` ستون `deleted_at` را اضافه می‌کند. هر سه column از نوع `DATETIME NULL`، بدون default و بدون automatic update/delete behavior هستند. Builder آن‌ها را به column ASTهای عادی گسترش می‌دهد؛ duplicate column nameها در Builder و Renderer پس از trim و case-insensitive comparison رد می‌شوند. مثال versioned در `create_table_timestamps` ثبت شده و DDL واقعی همچنان به opt-in نیاز دارد.
