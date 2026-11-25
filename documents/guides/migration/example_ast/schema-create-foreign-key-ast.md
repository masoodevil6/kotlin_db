# SchemaCreateForeignKeyAst

## موضوع

افزودن Foreign Key به جدول موجود.

## ساختار AST

- `tableName` و `constraintName`.
- `columnNames`: ستون‌های جدول فعلی.
- `referencesTableName` و `referencesColumnNames`.
- `onDelete` و `onUpdate`: اختیاری؛ `CASCADE`، `RESTRICT`، `NO ACTION` یا `SET NULL`.

## دستور ساخت AST

```kotlin
Schema.createForeignKey {
    tableName("orders")
    name("fk_orders_customer")
    columns("customer_id")
    referencesTable("customers")
    referencesColumns("id")
    onDelete(SchemaForeignKeyAction.CASCADE)
}
```

## Query تولیدی

```sql
ALTER TABLE `table` ADD CONSTRAINT `fk_name`
FOREIGN KEY (`local_id`) REFERENCES `parent` (`id`)
[ON DELETE action] [ON UPDATE action]
```
