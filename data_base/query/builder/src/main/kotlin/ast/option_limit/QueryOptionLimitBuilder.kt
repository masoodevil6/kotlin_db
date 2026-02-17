package gog.my_project.data_base.query.builder.ast.option_limit

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.option_limit.IQueryOptionLimitApi
import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.schema.option_limit.QueryOptionLimitAst

class QueryOptionLimitBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>() ,
    override var ast: IQueryOptionLimitAst = QueryOptionLimitAst(),
) : IQueryOptionLimitApi {


    override fun setOptionLimit(
        optionLimit: Long
    ): IQueryOptionLimitApi
    {
        this.ast.pageLimit = optionLimit;
        return this;
    }


}