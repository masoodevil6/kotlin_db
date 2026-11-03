package gog.my_project.data_base_schema.ast.interfaces.create_table.render_schema_create_table

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.ISchemaCreateTableDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.table.ISchemaTableAst
import gog.my_project.data_base_schema.ast.metadata.create_table.render_schema_create_table.SchemaRenderCreateTableAstMetadata

interface ISchemaRenderCreateTableAst : ISchemaAst<SchemaRenderCreateTableAstMetadata> {

    var schemaTableAst:    ISchemaTableAst?;
    var columns:              MutableList<ISchemaColumnAst>;
    var definitions:          MutableList<ISchemaCreateTableDefinitionAst<*>>;
}
