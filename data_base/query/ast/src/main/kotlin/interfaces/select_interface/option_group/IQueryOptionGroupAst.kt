package gog.my_project.data_base.query.ast.interfaces.select_interface.option_group

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst

interface IQueryOptionGroupAst  : IQueryAst {

    val groupByList: MutableList<IQueryColumnsBaseAst>;

}