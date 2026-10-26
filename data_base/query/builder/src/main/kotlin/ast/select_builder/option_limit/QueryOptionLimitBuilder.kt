package gog.my_project.data_base.query.builder.ast.select_builder.option_limit

import gog.my_project.data_base.query.api.interfaces.api.select_api.option_limit.IQueryOptionLimitApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_limit.QueryOptionLimitAst

class QueryOptionLimitBuilder(
    override var ast: IQueryOptionLimitAst = QueryOptionLimitAst(),
) : IQueryOptionLimitApi {

    override fun setOptionLimit(
        optionLimit: Long
    ): IQueryOptionLimitApi
    {
        this.ast.metadata.pageLimit = optionLimit;
        return this;
    }

}