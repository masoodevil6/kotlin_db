package gog.my_project.query.query_builder.tools.options

import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOffset
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect

class QueryToolsOptionOffset(
    override  var params: MutableList<Any?> = mutableListOf<Any?>()
):
    IQueryToolsOptionOffset
{
    var _pageOffset : Long? = null;


    /* ==============================================================
    template
    ============================================================== */
    override fun getOptionOffset(): Long? {
        return _pageOffset;
    }





    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getOptionOffsetSql(this);
    }



    /* ==============================================================
    structure
    ============================================================== */
    override fun setOptionOffset(optionOffset: Long) : IQueryToolsOptionOffset {
        this._pageOffset = optionOffset;
        return this;
    }

}