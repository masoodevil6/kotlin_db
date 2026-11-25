# SchemaRenderCreateTableAst

## موضوع

AST اصلی ساخت جدول؛ نام جدول، ستون‌ها و تعریف‌های کلید و قید را در یک دستور `CREATE TABLE` ترکیب می‌کند.

## ساختار AST

- `schemaTableAst`: یک `SchemaTableAst` شامل نام جدول.
- `columns`: فهرست `SchemaColumnAst`.
- `definitions`: فهرست تعریف‌های `PrimaryKeyDefinitionAst`، `UniqueDefinitionAst`، `IndexDefinitionAst`، `FullTextIndexDefinitionAst` و `ForeignKeyDefinitionAst`.
- `ifNotExists`: در صورت فعال‌بودن، `IF NOT EXISTS` به دستور اضافه می‌شود.

## دستور ساخت AST

```kotlin
Schema.createTable {
    table { tableName("users") }
    addColumn {
        name("id")
        dataType(SchemaDataType.IntType)
        notNull()
        autoIncrement()
    }
    primaryKey { columns("id") }
    ifNotExists()
}
```

## Query تولیدی

```sql
CREATE TABLE [IF NOT EXISTS] `table` (
  `column` TYPE NULL|NOT NULL,
  PRIMARY KEY (`id`),
  ...
)
```

ستون‌ها و تعریف‌ها به ترتیب ثبت‌شده در بدنهٔ دستور می‌آیند. نحو نهایی نوع داده و dialect انتخاب‌شده را دنبال می‌کند.
