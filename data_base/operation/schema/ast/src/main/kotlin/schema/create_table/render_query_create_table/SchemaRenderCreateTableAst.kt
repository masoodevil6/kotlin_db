package gog.my_project.data_base_schema.ast.schema.create_table.render_schema_create_table

import gog.my_project.data_base_schema.ast.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.ISchemaCreateTableDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.table.ISchemaTableAst
import gog.my_project.data_base_schema.ast.metadata.create_table.render_schema_create_table.SchemaRenderCreateTableAstMetadata

class SchemaRenderCreateTableAst(
    override var metadata: SchemaRenderCreateTableAstMetadata = SchemaRenderCreateTableAstMetadata()
) : ISchemaRenderCreateTableAst {
    override var schemaTableAst: ISchemaTableAst? = null;
    override var columns:           MutableList<ISchemaColumnAst> = mutableListOf();
    override var definitions:       MutableList<ISchemaCreateTableDefinitionAst<*>> = mutableListOf();
}
