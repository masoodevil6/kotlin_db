package gog.my_project.data_base.query.ast.schema.option_group

import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.option_group.IQueryOptionGroupAst

class QueryOptionGroupAst : IQueryOptionGroupAst {

    override val groupByList: MutableList<IQueryColumnsBaseAst> = mutableListOf()

}