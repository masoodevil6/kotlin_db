package gog.my_project.data_base.query.builder.interfaces.option_limit

import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.builder.interfaces.IQuery

interface IQueryOptionLimitBuilder : IQuery {

    var ast: IQueryOptionLimitAst;

    fun setOptionLimit(optionLimit: Long) : IQueryOptionLimitBuilder;

}