# SchemaColumnAst

## موضوع

تعریف یک ستون؛ در ساخت جدول یا AST افزودن ستون به کار می‌رود.

## ساختار AST

- `columnName` و `columnDataType`.
- `columnDefault` به‌همراه `hasColumnDefault` برای تمایزِ نبودن عبارت پیش‌فرض از `DEFAULT NULL`.
- `columnNullable`، `columnAutoIncrement` و `columnPrimary`.

## دستور ساخت AST

ستون داخل `CREATE TABLE`:

```kotlin
Schema.createTable {
    table { tableName("users") }
    addColumn {
        name("email")
        dataType(SchemaDataType.VarcharType(255))
        notNull()
        default("unknown")
    }
}
```

همین ساختار ستون را `Schema.addColumn { ... }` نیز برای افزودن ستون به جدول موجود می‌پذیرد.

## Query تولیدی

دستور مستقل تولید نمی‌کند. قطعهٔ ستون در SQL به این شکل است:

```sql
`column` TYPE NULL|NOT NULL [DEFAULT value] [AUTO_INCREMENT] [PRIMARY KEY]
```
