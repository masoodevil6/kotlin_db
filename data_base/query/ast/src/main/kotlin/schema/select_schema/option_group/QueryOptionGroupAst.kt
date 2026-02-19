package gog.my_project.data_base.query.ast.schema.select_schema.option_group

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_group.IQueryOptionGroupAst


class QueryOptionGroupAst :
    IQueryOptionGroupAst {

    override val groupByList: MutableList<IQueryColumnsBaseAst> = mutableListOf()

}