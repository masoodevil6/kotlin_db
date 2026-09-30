# ساختار ماژول `data_base:migration:ast`

مدل‌سازی AST مربوط به جدول، ستون و عملیات create table.

## نمودار درختی

```text
data_base:migration:ast
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   ├── create_table
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── IMigrationColumnAst.kt
│   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   └── IMigrationRenderCreateTableAst.kt
│   │   │   │   │   └── table
│   │   │   │   │       └── IMigrationTableAst.kt
│   │   │   │   └── IMigrationAst.kt
│   │   │   ├── schema
│   │   │   │   └── create_table
│   │   │   │       ├── column
│   │   │   │       │   └── MigrationColumnAst.kt
│   │   │   │       ├── render_query_create_table
│   │   │   │       │   └── MigrationRenderCreateTableAst.kt
│   │   │   │       └── table
│   │   │   │           └── MigrationTableAst.kt
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

## AST مربوط به `DROP COLUMN` (v1.3)

- `interfaces/drop_column/IMigrationDropColumnAst.kt`: قرارداد AST با نام جدول، ستون و `ifExists`.
- `schema/drop_column/MigrationDropColumnAst.kt`: پیاده‌سازی AST مستقل از renderer و executor؛ `ifExists` به‌صورت پیش‌فرض false است.
# `RENAME COLUMN` (v1.4)

`IMigrationRenameColumnAst` و `MigrationRenameColumnAst` در پوشه‌های `rename_column` تنها identifier جدول، ستون مبدأ و ستون مقصد را نگه می‌دارند؛ type، default و ویژگی‌های تعریف ستون در این AST نیستند.

# AST مربوط به `MODIFY COLUMN` (v1.5)

`interfaces/modify_column/IMigrationModifyColumnAst.kt` و `schema/modify_column/MigrationModifyColumnAst.kt` یک operation AST مستقل دارند. nullability فقط `NULL` یا `NOT_NULL` است؛ default یکی از `NoDefaultClause`، `DefaultNull` و `DefaultValue(value)` است و `autoIncrement` Boolean مطلوب را نگه می‌دارد. AST فقط desired definition را مدل می‌کند و به state ستون موجود وابسته نیست.

# AST مربوط به `CREATE INDEX` (v1.6)

`interfaces/create_index/IMigrationCreateIndexAst.kt` و `schema/create_index/MigrationCreateIndexAst.kt` سه identifier مستقل و nullable برای validation دفاعی renderer نگه می‌دارند: `tableName`، `indexName` و `columnName`. از v1.12، `indexMethod: IndexMethod?` نیز اختیاری است؛ enum در `interfaces/create_index/IndexMethod.kt` فقط `BTREE` و `HASH` دارد. AST unique flag یا لیست ستون‌ها ندارد.

# AST مربوط به `DROP INDEX` (v1.7)

`interfaces/drop_index/IMigrationDropIndexAst.kt` و `schema/drop_index/MigrationDropIndexAst.kt` فقط دو identifier nullable برای `tableName` و `indexName` نگه می‌دارند تا Renderer بتواند AST ناقص مستقیم را رد کند. `PRIMARY` مقدار عادی `indexName` است.

# AST مربوط به `CREATE UNIQUE INDEX` (v1.8)

`interfaces/create_unique_index/IMigrationCreateUniqueIndexAst.kt` و `schema/create_unique_index/MigrationCreateUniqueIndexAst.kt` سه identifier nullable یعنی `tableName`، `indexName` و `columnName` دارند. AST پرچم `unique` ندارد؛ نوع operation آن را مشخص می‌کند.

# AST مربوط به `CREATE MULTI-COLUMN INDEX` (v1.9)

`interfaces/create_multi_column_index/IMigrationCreateMultiColumnIndexAst.kt` و `schema/create_multi_column_index/MigrationCreateMultiColumnIndexAst.kt` سه property نگه می‌دارند: `tableName`، `indexName` و ordered `columnNames: List<String>?`. فهرست ستون‌ها مرتب‌سازی نمی‌شود و AST فلگ unique ندارد.

# AST مربوط به `CREATE FULLTEXT INDEX` (v1.10)

`interfaces/create_full_text_index/IMigrationCreateFullTextIndexAst.kt` و `schema/create_full_text_index/MigrationCreateFullTextIndexAst.kt` یک AST مستقل دارند: `tableName`، `indexName` و ordered `columnNames: List<String>?`. حداقل یک ستون لازم است؛ این AST Column Definition، parser option یا metadata جدول را نگه نمی‌دارد.

# AST مربوط به Foreign Key (v1.13)

ASTهای مستقل create و drop در `interfaces/create_foreign_key/` و `interfaces/drop_foreign_key/` قرار دارند. Create جفت ordered ستون‌های محلی و مرجع، جدول مرجع، نام constraint و actionهای اختیاری را نگه می‌دارد؛ `ForeignKeyAction` فقط `CASCADE`، `RESTRICT`، `NO_ACTION` و `SET_NULL` را دارد. Drop فقط جدول و نام constraint را نگه می‌دارد.

# ASTهای table-level در `CREATE TABLE` (v1.14)

`IMigrationRenderCreateTableAst` یک `definitions` list مرتب و typed اضافه می‌کند. `IMigrationCreateTableDefinitionAst` زیرنوع‌های PK، UNIQUE، INDEX، FULLTEXT و FK را نشانه‌گذاری می‌کند؛ fieldهای هر subtype مشخص و بدون `Any`, map یا raw SQL هستند. ترتیب تعریف‌ها تا renderer حفظ می‌شود.
