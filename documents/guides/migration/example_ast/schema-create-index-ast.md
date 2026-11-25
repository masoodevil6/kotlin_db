# SchemaCreateIndexAst

## موضوع

ساخت Index معمولی برای یک ستون.

## ساختار AST

- `tableName`، `indexName` و `columnName`.
- `indexMethod: IndexMethod?`: اختیاری؛ `BTREE` یا `HASH`.

## دستور ساخت AST

```kotlin
Schema.createIndex {
    tableName("users")
    name("idx_users_email")
    column("email")
    using(SchemaIndexMethod.BTREE)
}
```

## Query تولیدی

```sql
CREATE INDEX `idx_name` ON `table` (`column`) [USING BTREE|HASH]
```
