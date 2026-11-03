package gog.my_project.data_base_schema.ast.metadata.create_table.render_schema_create_table

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaRenderCreateTableAstMetadata : ISchemaAstMetadata {
    var ifNotExists:          Boolean = false
}
