package gog.my_project.data_base.query_builder.query.ast.options

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOffset

class QueryToolsOptionOffset(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
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
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getOptionOffsetSql(this);
    }*/



    /* ==============================================================
    structure
    ============================================================== */
    override fun setOptionOffset(optionOffset: Long) : IQueryToolsOptionOffset {
        this._pageOffset = optionOffset;
        return this;
    }

}