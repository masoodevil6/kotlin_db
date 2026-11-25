# ASTهای Schema در Migration

هر فایل زیر یک نوع AST را جداگانه معرفی می‌کند: موضوع آن، داده‌هایی که در ساختارش نگه می‌دارد، و SQL یا قطعهٔ SQL متناظر. ASTهای Table، Column، شرط وجود و تعریف‌های تو‌در‌توی `CREATE TABLE` به‌تنهایی دستور کامل SQL تولید نمی‌کنند؛ خروجی آن‌ها داخل دستور والد قرار می‌گیرد.

## ASTهای ترکیبی و اجزای `CREATE TABLE`

- [SchemaRenderCreateTableAst](schema-render-create-table-ast.md)
- [SchemaTableAst](schema-table-ast.md)
- [SchemaColumnAst](schema-column-ast.md)
- [SchemaIfExistAst](schema-if-exist-ast.md)
- [PrimaryKeyDefinitionAst](primary-key-definition-ast.md)
- [UniqueDefinitionAst](unique-definition-ast.md)
- [IndexDefinitionAst](index-definition-ast.md)
- [FullTextIndexDefinitionAst](full-text-index-definition-ast.md)
- [ForeignKeyDefinitionAst](foreign-key-definition-ast.md)

## ASTهای تغییر Table و Column

- [SchemaAddColumnAst](schema-add-column-ast.md)
- [SchemaDropColumnAst](schema-drop-column-ast.md)
- [SchemaRenameColumnAst](schema-rename-column-ast.md)
- [SchemaModifyColumnAst](schema-modify-column-ast.md)
- [SchemaRenameTableAst](schema-rename-table-ast.md)
- [SchemaDropTableAst](schema-drop-table-ast.md)

## ASTهای Index و Foreign Key

- [SchemaCreateIndexAst](schema-create-index-ast.md)
- [SchemaCreateUniqueIndexAst](schema-create-unique-index-ast.md)
- [SchemaCreateMultiColumnIndexAst](schema-create-multi-column-index-ast.md)
- [SchemaCreateFullTextIndexAst](schema-create-full-text-index-ast.md)
- [SchemaCreateSpatialIndexAst](schema-create-spatial-index-ast.md)
- [SchemaDropIndexAst](schema-drop-index-ast.md)
- [SchemaCreateForeignKeyAst](schema-create-foreign-key-ast.md)
- [SchemaDropForeignKeyAst](schema-drop-foreign-key-ast.md)

فهرست اصلی راهنما: [راهنمای Migration](../README.md)
