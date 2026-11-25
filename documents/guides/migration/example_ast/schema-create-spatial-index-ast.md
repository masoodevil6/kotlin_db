# SchemaCreateSpatialIndexAst

## موضوع

ساخت Spatial Index روی یک ستون.

## ساختار AST

- `tableName`، `indexName` و `columnName`.

## دستور ساخت AST

```kotlin
Schema.createSpatialIndex {
    tableName("places")
    name("sp_places_location")
    column("location")
}
```

## Query تولیدی

```sql
CREATE SPATIAL INDEX `sp_name` ON `table` (`geometry_column`)
```
