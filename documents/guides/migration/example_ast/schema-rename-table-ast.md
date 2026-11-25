# SchemaRenameTableAst

## موضوع

تغییر نام جدول.

## ساختار AST

- `fromTableName`: نام فعلی.
- `toTableName`: نام مقصد.

## دستور ساخت AST

```kotlin
Schema.renameTable {
    fromTableName("users")
    toTableName("customers")
}
```

## Query تولیدی

```sql
RENAME TABLE `old_table` TO `new_table`
```
