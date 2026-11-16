# پوشش Schema DSL و مثال‌های ماژول

ماژول `data_base:migration:example` از Schema DSL در `data_base:operation:schema` برای ساخت `SchemaOperation` استفاده می‌کند. نمونه‌ها SQL را با `Schema.render(operation, dialect, version)` تولید می‌کنند؛ این کار به دیتابیس متصل نمی‌شود و DDL را اجرا نمی‌کند.

| عملیات Schema در Source | موارد نمونه | فایل نمونه |
|---|---|---|
| `Schema.createTable` | ستون، نوع داده، Default، nullability، auto increment و primary key | `create_table/A1ExampleCreateTableV1.kt` |
| `Schema.createTable` با تعریف constraint | unique، index و foreign key در تعریف جدول | `create_table_constraints/A1ExampleCreateTableConstraintsV1.kt` |
| `Schema.createTable` با shorthand زمان | `created_at`، `updated_at` و `deleted_at` | `create_table_timestamps/A1ExampleCreateTableTimestampsV1.kt` |
| `Schema.createTable` با typeهای زمانی | DATE، TIME، DATETIME و TIMESTAMP | `temporal_data_types/A1ExampleTemporalDataTypesV1.kt` |
| `Schema.addColumn` و `Schema.modifyColumn` | افزودن ستون و تغییر type، nullability، Default و auto increment | `add_column/A1ExampleAddColumnV1.kt`، `modify_column/A1ExampleModifyColumnV1.kt` |
| `Schema.renameColumn` و `Schema.dropColumn` | تغییرنام و حذف ستون؛ حذف با حالت `ifExists` در نمونه پوشش داده شده است | `rename_column/A1ExampleRenameColumnV1.kt`، `drop_column/A1ExampleDropColumnV1.kt` |
| `Schema.renameTable` و `Schema.dropTable` | تغییرنام و حذف جدول؛ حذف با حالت `ifExists` در نمونه پوشش داده شده است | `rename_table/A1ExampleRenameTableV1.kt`، `drop_table/A1ExampleDropTableV1.kt` |
| `Schema.createIndex`، `Schema.createUniqueIndex` و `Schema.createMultiColumnIndex` | index عادی، unique و چندستونی | `create_index/A1ExampleCreateIndexV1.kt`، `create_unique_index/A1ExampleCreateUniqueIndexV1.kt`، `create_multi_column_index/A1ExampleCreateMultiColumnIndexV1.kt` |
| `Schema.createFullTextIndex` و `Schema.createSpatialIndex` | full-text و spatial index | `create_full_text_index/A1ExampleCreateFullTextIndexV1.kt`، `create_spatial_index/A1ExampleCreateSpatialIndexV1.kt` |
| `Schema.createForeignKey` و `Schema.dropForeignKey` | تعریف و حذف foreign key | `create_foreign_key/A1ExampleCreateForeignKeyV1.kt`، `drop_foreign_key/A1ExampleDropForeignKeyV1.kt` |
| `Schema.dropIndex` | حذف index | `drop_index/A1ExampleDropIndexV1.kt` |
| Migration API | `Migration`, `MigrationDefinition`, Version/Group و `migrationConfig { ... }` | `composite/CreateCustomersMigrationV1.kt`، `registration/MigrationRegistrationExample.kt` |

این فایل‌ها مثال‌های Schema و Registration هستند و همهٔ مسیر اجرای Database Manager را نشان نمی‌دهند. برای تعریف Configuration جدا برای هر Database Registration و اجرای `handle.migration { ... }` به [نمونهٔ عمومی دو دیتابیس](../../../example/README.md) مراجعه کنید. رفتار فرمان‌ها در [راهنمای Execution](execution.md) آمده است.
