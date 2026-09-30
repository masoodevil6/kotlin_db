package gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst

class QueryColumnsBaseAst() :
    IQueryColumnsBaseAst {

    override var tableAttribute:     String?                 = null;

    override var tableAlias:         String?                 = null;
    override var column:             String?                 = null;

    override var cteAlias:           String?                 = null;
    override var select:             String?                 = null;

}