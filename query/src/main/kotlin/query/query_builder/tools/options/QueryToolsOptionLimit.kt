package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionLimit
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect

class QueryToolsOptionLimit(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
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
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getOptionLimitSql(this);
    }




    /* ==============================================================
    structure
    ============================================================== */
    override fun setOptionLimit(optionLimit: Long) : IQueryToolsOptionLimit {
        this._pageLimit = optionLimit;
        return this;
    }



}