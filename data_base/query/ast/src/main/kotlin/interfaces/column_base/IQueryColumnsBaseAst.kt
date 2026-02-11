package gog.my_project.data_base.query.ast.interfaces.column_base

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryColumnsBaseAst  : IQueryAst{

    var table:          String?;
    var tableAlias:     String?;
    var column:         String?;

    var cte:            String?;
    var cteAlias:       String?;
    var select:         String?;

}