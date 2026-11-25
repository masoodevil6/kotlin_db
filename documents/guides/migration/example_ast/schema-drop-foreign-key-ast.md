# SchemaDropForeignKeyAst

## موضوع

حذف Foreign Key از جدول.

## ساختار AST

- `tableName` و `constraintName`.
- `ifExistAst: SchemaIfExistAst?`: شرط اختیاری.

## دستور ساخت AST

```kotlin
Schema.dropForeignKey {
    tableName("orders")
    name("fk_orders_customer")
    ifExists()
}
```

## Query تولیدی

```sql
ALTER TABLE `table` DROP FOREIGN KEY [IF EXISTS] `fk_name`
```

پشتیبانی از `IF EXISTS` به dialect منتخب وابسته است.
