package gog.my_project.data_base.query_builder.query.ast.options

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionLimit

class QueryToolsOptionLimit(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsOptionLimit
{

    var _pageLimit : Long? = null;

    /* ==============================================================
    template
    ============================================================== */
    override fun getOptionLimit(): Long? {
        return _pageLimit
    }




    /* ==============================================================
    Builder
    ============================================================== */
   /* override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getOptionLimitSql(this);
    }*/




    /* ==============================================================
    structure
    ============================================================== */
    override fun setOptionLimit(optionLimit: Long) : IQueryToolsOptionLimit {
        this._pageLimit = optionLimit;
        return this;
    }



}