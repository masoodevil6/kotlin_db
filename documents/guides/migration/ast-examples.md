# پوشش AST و مثال‌های ماژول

AST عمومی schema در `data_base/migration/ast` روی `IMigrationAst` بنا شده است. جدول زیر خانواده‌های AST را با مثال‌های متناظر وصل می‌کند؛ ردیف پایانی API مدل migration و ثبت نسخه را جداگانه نشان می‌دهد:

| خانوادهٔ AST | اطلاعات اصلی ثبت‌شده | مثال ماژول |
|---|---|---|
| `IMigrationRenderCreateTableAst`، `IMigrationTableAst` و `IMigrationColumnAst` | جدول، ستون‌ها، نوع، default، nullability، auto increment، primary key و `ifNotExists` | `create_table/A1ExampleCreateTableV1.kt` |
| تعریف‌های `IMigrationCreateTableDefinitionAst` | primary key، unique، index، full-text و foreign key در تعریف جدول | `create_table_constraints/A1ExampleCreateTableConstraintsV1.kt` |
| shorthandهای ستون زمانی | ستون‌های `created_at`، `updated_at` و `deleted_at` | `create_table_timestamps/A1ExampleCreateTableTimestampsV1.kt` |
| typeهای زمانی | DATE، TIME، DATETIME و TIMESTAMP با precisionهای نمونه | `temporal_data_types/A1ExampleTemporalDataTypesV1.kt` |
| `IMigrationAddColumnAst` و `IMigrationModifyColumnAst` | افزودن ستون و تغییر نوع، nullability، default و auto increment | `add_column/A1ExampleAddColumnV1.kt`، `modify_column/A1ExampleModifyColumnV1.kt` |
| `IMigrationRenameColumnAst` و `IMigrationDropColumnAst` | تغییرنام و حذف ستون؛ حذف می‌تواند `ifExists` داشته باشد | `rename_column/A1ExampleRenameColumnV1.kt`، `drop_column/A1ExampleDropColumnV1.kt` |
| `IMigrationRenameTableAst` و `IMigrationDropTableAst` | تغییرنام و حذف جدول؛ حذف می‌تواند `ifExists` داشته باشد | `rename_table/A1ExampleRenameTableV1.kt`، `drop_table/A1ExampleDropTableV1.kt` |
| `IMigrationCreateIndexAst`، `IMigrationCreateUniqueIndexAst` و `IMigrationCreateMultiColumnIndexAst` | index عادی با روش `BTREE` یا `HASH`، unique و index چندستونی | `create_index/A1ExampleCreateIndexV1.kt`، `create_unique_index/A1ExampleCreateUniqueIndexV1.kt`، `create_multi_column_index/A1ExampleCreateMultiColumnIndexV1.kt` |
| `IMigrationCreateFullTextIndexAst` و `IMigrationCreateSpatialIndexAst` | full-text چندستونی و spatial تک‌ستونی | `create_full_text_index/A1ExampleCreateFullTextIndexV1.kt`، `create_spatial_index/A1ExampleCreateSpatialIndexV1.kt` |
| `IMigrationCreateForeignKeyAst` و `IMigrationDropForeignKeyAst` | constraint، ستون‌های محلی و مرجع، و actionهای حذف/به‌روزرسانی | `create_foreign_key/A1ExampleCreateForeignKeyV1.kt`، `drop_foreign_key/A1ExampleDropForeignKeyV1.kt` |
| `IMigrationDropIndexAst` | نام جدول و index برای حذف | `drop_index/A1ExampleDropIndexV1.kt` |
| APIهای `Migration`، `MigrationDefinition` و `MigrationConfiguration` (چرخهٔ ثبت، نه AST schema) | فهرست عملیات `up/down` و ثبت migration منفرد/گروهی | `composite/CreateCustomersMigrationV1.kt`، `registration/MigrationRegistrationExample.kt` |

مثال‌های مستقل ADD/MODIFY/RENAME/DROP و indexها متد `migration()` را برای ساخت AST ارائه می‌کنند، سپس `render(dialect)` را برای مشاهدهٔ DDL و `execute(executor)` را برای اجرای صریح به‌کار می‌برند. بعضی مثال‌ها تنها AST یا SQL را برای نمایش می‌سازند؛ نتیجهٔ `render` را با اجرای واقعی روی دیتابیس یکی نگیرید. نمونه‌های این صفحه رفتار را شرح می‌دهند و اجرای Migration را در محیط کاربر انجام نمی‌دهند.
