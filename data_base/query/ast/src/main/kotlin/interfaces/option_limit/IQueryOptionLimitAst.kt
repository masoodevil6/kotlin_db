package gog.my_project.data_base.query.ast.interfaces.option_limit

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryOptionLimitAst  : IQueryAst {

    var pageLimit : Long?;

}