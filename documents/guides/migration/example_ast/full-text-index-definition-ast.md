# FullTextIndexDefinitionAst

## موضوع

تعریف Full-Text Index داخل AST ساخت جدول.

## ساختار AST

- `indexName: String`.
- `columnNames: List<String>`: یک یا چند ستون.

## دستور ساخت AST

```kotlin
Schema.createTable {
    table { tableName("articles") }
    addColumn {
        name("body")
        dataType(SchemaDataType.TextType)
    }
    fullTextIndex {
        name("ft_articles_body")
        columns("body")
    }
}
```

## Query تولیدی

قطعهٔ داخل `CREATE TABLE`:

```sql
FULLTEXT INDEX `ft_body` (`body`)
```
