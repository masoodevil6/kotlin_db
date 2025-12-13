package gog.kotlin_db.data.base.query_builder.tools.option_offset

import gog.kotlin_db.data.base.query_builder.tools.IQueryTools

interface IQueryToolsOptionOffset: IQueryTools {

    fun setOptionOffset(optionOffset: Int) : QueryToolsOptionOffset;

}