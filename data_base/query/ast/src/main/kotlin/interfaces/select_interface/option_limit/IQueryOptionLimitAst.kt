package gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryOptionLimitAst  : IQueryAst {

    var pageLimit : Long?;

}