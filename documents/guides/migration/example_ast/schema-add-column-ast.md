# SchemaAddColumnAst

## موضوع

افزودن یک ستون به جدول موجود.

## ساختار AST

- `tableName: String?`.
- `columnAst: SchemaColumnAst?`: تعریف نام، نوع و ویژگی‌های ستون.

## دستور ساخت AST

```kotlin
Schema.addColumn {
    tableName("users")
    name("created_at")
    dataType(SchemaDataType.DateTimeType())
    notNull()
}
```

## Query تولیدی

```sql
ALTER TABLE `table` ADD COLUMN `column` TYPE NULL|NOT NULL [DEFAULT value] [AUTO_INCREMENT] [PRIMARY KEY]
```
