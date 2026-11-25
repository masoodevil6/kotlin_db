# SchemaRenameColumnAst

## موضوع

تغییر نام یک ستون در جدول.

## ساختار AST

- `tableName`: جدول.
- `name`: نام فعلی ستون.
- `to`: نام جدید ستون.

## دستور ساخت AST

```kotlin
Schema.renameColumn {
    tableName("users")
    name("display_name")
    to("full_name")
}
```

## Query تولیدی

بسته به dialect و نسخهٔ هدف، Renderer یکی از این صورت‌ها را می‌سازد:

```sql
ALTER TABLE `table` RENAME COLUMN `old_name` TO `new_name`
```

یا در مسیر legacy:

```sql
ALTER TABLE `table` CHANGE COLUMN `old_name` `new_name` ...
```
