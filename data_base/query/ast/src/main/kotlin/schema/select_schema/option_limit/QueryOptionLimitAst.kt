package gog.my_project.data_base.query.ast.schema.select_schema.option_limit

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst

class QueryOptionLimitAst :
    IQueryOptionLimitAst {

    override  var pageLimit : Long?                 = null;

}