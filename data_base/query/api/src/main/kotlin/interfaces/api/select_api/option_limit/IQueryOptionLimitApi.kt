package gog.my_project.data_base.query.api.interfaces.api.select_api.option_limit

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst

interface IQueryOptionLimitApi : IQueryApi<IQueryOptionLimitAst> {

    fun setOptionLimit(optionLimit: Long) : IQueryOptionLimitApi;

}