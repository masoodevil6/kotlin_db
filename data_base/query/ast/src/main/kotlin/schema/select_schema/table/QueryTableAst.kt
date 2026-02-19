package gog.my_project.data_base.query.ast.schema.select_schema_ast.table

import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst


class QueryTableAst : IQueryTableAst {

    override var table :      String?          = null;
    override var tableAlias : String?          = null;
    override var cte :        String?          = null;
    override var cteAlias :   String?          = null;

}