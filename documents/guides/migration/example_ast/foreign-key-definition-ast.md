# ForeignKeyDefinitionAst

## موضوع

تعریف Foreign Key داخل AST ساخت جدول.

## ساختار AST

- `constraintName`.
- `columnNames`: ستون‌های محلی.
- `referencesTableName` و `referencesColumnNames`.
- `onDelete` و `onUpdate`: اختیاری؛ از `CASCADE`، `RESTRICT`، `NO ACTION` و `SET NULL`.

## دستور ساخت AST

```kotlin
Schema.createTable {
    table { tableName("orders") }
    addColumn {
        name("customer_id")
        dataType(SchemaDataType.IntType)
        notNull()
    }
    foreignKey {
        name("fk_orders_customer")
        columns("customer_id")
        referencesTable("customers")
        referencesColumns("id")
        onDelete(SchemaForeignKeyAction.CASCADE)
    }
}
```

## Query تولیدی

قطعهٔ داخل `CREATE TABLE`:

```sql
CONSTRAINT `fk_name` FOREIGN KEY (`local_id`)
REFERENCES `parent` (`id`) [ON DELETE action] [ON UPDATE action]
```
