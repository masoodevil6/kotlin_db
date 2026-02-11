package gog.my_project.data_base.query.ast.interfaces.option_group

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst

interface IQueryOptionGroupAst  : IQueryAst {

    val groupByList: MutableList<IQueryColumnsBaseAst>;

}