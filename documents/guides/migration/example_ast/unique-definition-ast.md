# UniqueDefinitionAst

## موضوع

تعریف قید Unique نام‌دار داخل AST ساخت جدول.

## ساختار AST

- `indexName: String`: نام قید.
- `columnNames: List<String>`: ستون‌های یکتا.

## دستور ساخت AST

```kotlin
Schema.createTable {
    table { tableName("users") }
    addColumn {
        name("email")
        dataType(SchemaDataType.VarcharType(255))
        notNull()
    }
    unique {
        name("uq_users_email")
        columns("email")
    }
}
```

## Query تولیدی

قطعهٔ داخل `CREATE TABLE`:

```sql
CONSTRAINT `uq_name` UNIQUE (`email`)
```
