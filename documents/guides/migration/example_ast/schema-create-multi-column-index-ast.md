# SchemaCreateMultiColumnIndexAst

## موضوع

ساخت Index معمولی روی چند ستون.

## ساختار AST

- `tableName` و `indexName`.
- `columnNames: List<String>`.

## دستور ساخت AST

```kotlin
Schema.createMultiColumnIndex {
    tableName("users")
    name("idx_users_name")
    columns("first_name", "last_name")
}
```

## Query تولیدی

```sql
CREATE INDEX `idx_name` ON `table` (`first_column`, `second_column`)
```
