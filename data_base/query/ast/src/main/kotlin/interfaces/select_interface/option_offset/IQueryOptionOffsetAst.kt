package gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset

import gog.my_project.data_base.query.ast.interfaces.IQueryAst

interface IQueryOptionOffsetAst : IQueryAst  {

    var pageOffset : Long?;

}