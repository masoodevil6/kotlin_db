# SchemaDropIndexAst

## موضوع

حذف Index از جدول.

## ساختار AST

- `tableName` و `indexName`.
- `ifExistAst: SchemaIfExistAst?`: شرط اختیاری.

## دستور ساخت AST

```kotlin
Schema.dropIndex {
    tableName("users")
    name("idx_users_email")
    ifExists()
}
```

## Query تولیدی

```sql
DROP INDEX [IF EXISTS] `idx_name` ON `table`
```

پشتیبانی از `IF EXISTS` به dialect منتخب وابسته است.
