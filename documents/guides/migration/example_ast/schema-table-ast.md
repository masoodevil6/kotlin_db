# SchemaTableAst

## موضوع

گرهٔ نام جدول که در AST ساخت جدول استفاده می‌شود.

## ساختار AST

- `tableName: String?`: نام جدول در Metadata.

## دستور ساخت AST

`SchemaTableAst` از بخش `table` در DSL ساخت جدول ایجاد می‌شود:

```kotlin
Schema.createTable {
    table { tableName("users") }
    addColumn {
        name("id")
        dataType(SchemaDataType.IntType)
        notNull()
    }
}
```

## Query تولیدی

دستور مستقل تولید نمی‌کند؛ نام نقل‌قول‌شدهٔ جدول را برای AST والد می‌سازد:

```sql
`table`
```
