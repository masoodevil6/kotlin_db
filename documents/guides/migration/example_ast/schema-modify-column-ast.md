# SchemaModifyColumnAst

## موضوع

تغییر تعریف ستون موجود؛ نوع داده و nullability صریح‌اند.

## ساختار AST

- `tableName` و `columnName`.
- `columnDataType`.
- `nullability`: یکی از `NULL` یا `NOT_NULL`.
- `defaultDefinition`: بدون عبارت پیش‌فرض، `DEFAULT NULL` یا مقدار پیش‌فرض.
- `autoIncrement: Boolean`.

## دستور ساخت AST

```kotlin
Schema.modifyColumn {
    tableName("users")
    name("age")
    dataType(SchemaDataType.IntType)
    notNull()
    default(0)
}
```

## Query تولیدی

```sql
ALTER TABLE `table` MODIFY COLUMN `column` TYPE NULL|NOT NULL [DEFAULT value] [AUTO_INCREMENT]
```
