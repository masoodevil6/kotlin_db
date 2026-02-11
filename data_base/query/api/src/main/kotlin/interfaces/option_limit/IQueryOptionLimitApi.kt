package gog.my_project.data_base.query.api.interfaces.option_limit

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst

interface IQueryOptionLimitApi : IQueryApi {

    var ast: IQueryOptionLimitAst;

    fun setOptionLimit(optionLimit: Long) : IQueryOptionLimitApi;

}