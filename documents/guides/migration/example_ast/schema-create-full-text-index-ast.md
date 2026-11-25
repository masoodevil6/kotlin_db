# SchemaCreateFullTextIndexAst

## موضوع

ساخت Full-Text Index روی یک یا چند ستون.

## ساختار AST

- `tableName` و `indexName`.
- `columnNames: List<String>`.

## دستور ساخت AST

```kotlin
Schema.createFullTextIndex {
    tableName("articles")
    name("ft_articles_content")
    columns("title", "body")
}
```

## Query تولیدی

```sql
CREATE FULLTEXT INDEX `ft_name` ON `table` (`title`, `body`)
```
