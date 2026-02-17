package gog.my_project.data_base.query.builder.interfaces.builder.option_limit

import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery

interface IQueryOptionLimitBuilder : IQuery {

    var ast: IQueryOptionLimitAst;

    fun setOptionLimit(optionLimit: Long) : IQueryOptionLimitBuilder;

}