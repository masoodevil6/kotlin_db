package gog.my_project.data_base.query.builder.ast.options

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionOffset
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionOffset

class QueryToolsOptionOffset(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsOptionOffset , IQueryDefinitionOptionOffset
{
    var _pageOffset : Long? = null;


    /* ==============================================================
    template
    ============================================================== */
    override fun getOptionOffset(): Long? {
        return _pageOffset;
    }


    /* ==============================================================
    structure
    ============================================================== */
    override fun setOptionOffset(optionOffset: Long) : IQueryDefinitionOptionOffset {
        this._pageOffset = optionOffset;
        return this;
    }

}