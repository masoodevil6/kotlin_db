# SchemaIfExistAst

## موضوع

گرهٔ شرطی `IF EXISTS` که به بعضی ASTهای حذف متصل می‌شود.

## ساختار AST

- Metadata بدون فیلد داده‌ای؛ وجود این گره نشان‌دهندهٔ فعال‌بودن شرط است.

## دستور ساخت AST

گره با فراخوانی `ifExists()` در یکی از DSLهای حذف ساخته می‌شود؛ برای نمونه:

```kotlin
Schema.dropTable {
    tableName("old_table")
    ifExists()
}
```

## Query تولیدی

دستور کامل نمی‌سازد؛ در محل پشتیبانی‌شده این قطعه را به دستور والد می‌افزاید:

```sql
IF EXISTS
```

پشتیبانی از این قطعه برای `DROP COLUMN`، `DROP INDEX` و `DROP FOREIGN KEY` به قابلیت dialect وابسته است.
