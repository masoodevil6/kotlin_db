# SchemaCreateUniqueIndexAst

## موضوع

ساخت Unique Index روی یک ستون.

## ساختار AST

- `tableName`، `indexName` و `columnName`.

## دستور ساخت AST

```kotlin
Schema.createUniqueIndex {
    tableName("users")
    name("uq_users_email")
    column("email")
}
```

## Query تولیدی

```sql
CREATE UNIQUE INDEX `uq_name` ON `table` (`column`)
```
