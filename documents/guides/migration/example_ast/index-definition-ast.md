# IndexDefinitionAst

## موضوع

تعریف Index معمولی داخل AST ساخت جدول.

## ساختار AST

- `indexName: String`.
- `columnNames: List<String>`.
- `indexMethod: IndexMethod?`: اختیاری؛ `BTREE` یا `HASH`.

## دستور ساخت AST

```kotlin
Schema.createTable {
    table { tableName("users") }
    addColumn {
        name("email")
        dataType(SchemaDataType.VarcharType(255))
    }
    index {
        name("idx_users_email")
        columns("email")
        using(SchemaIndexMethod.BTREE)
    }
}
```

## Query تولیدی

قطعهٔ داخل `CREATE TABLE`:

```sql
INDEX `idx_name` (`name`) [USING BTREE|HASH]
```
