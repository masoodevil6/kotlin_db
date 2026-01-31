package gog.my_project.data_base.query.builder.ast.options

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionLimit
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionLimit

class QueryToolsOptionLimit(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsOptionLimit , IQueryDefinitionOptionLimit
{

    var _pageLimit : Long? = null;

    /* ==============================================================
    template
    ============================================================== */
    override fun getOptionLimit(): Long? {
        return _pageLimit
    }



    /* ==============================================================
    structure
    ============================================================== */
    override fun setOptionLimit(optionLimit: Long) : IQueryDefinitionOptionLimit {
        this._pageLimit = optionLimit;
        return this;
    }



}