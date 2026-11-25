# SchemaDropTableAst

## موضوع

حذف جدول، با امکان فعال‌کردن شرط وجود.

## ساختار AST

- `tableName: String?`.
- `ifExistAst: SchemaIfExistAst?`.

## دستور ساخت AST

```kotlin
Schema.dropTable {
    tableName("temporary_users")
    ifExists()
}
```

## Query تولیدی

```sql
DROP TABLE [IF EXISTS] `table`
```
