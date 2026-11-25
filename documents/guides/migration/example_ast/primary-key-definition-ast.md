# PrimaryKeyDefinitionAst

## موضوع

تعریف Primary Key داخل AST ساخت جدول.

## ساختار AST

- `columnNames: List<String>`: ستون‌های کلید اصلی.

## دستور ساخت AST

```kotlin
Schema.createTable {
    table { tableName("users") }
    addColumn {
        name("id")
        dataType(SchemaDataType.IntType)
        notNull()
    }
    primaryKey { columns("id") }
}
```

## Query تولیدی

قطعهٔ داخل `CREATE TABLE`:

```sql
PRIMARY KEY (`id`)
```

برای چند ستون، نام‌ها با ویرگول در همان پرانتز می‌آیند.
