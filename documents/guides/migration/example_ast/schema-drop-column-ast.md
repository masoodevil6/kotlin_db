# SchemaDropColumnAst

## موضوع

حذف ستون از جدول.

## ساختار AST

- `tableName: String?`.
- `name: String?`: نام ستون.
- `ifExistAst: SchemaIfExistAst?`: شرط اختیاری `IF EXISTS`.

## دستور ساخت AST

```kotlin
Schema.dropColumn {
    tableName("users")
    name("legacy_code")
    ifExists()
}
```

## Query تولیدی

```sql
ALTER TABLE `table` DROP COLUMN [IF EXISTS] `column`
```

قابلیت `IF EXISTS` به dialect منتخب وابسته است.
