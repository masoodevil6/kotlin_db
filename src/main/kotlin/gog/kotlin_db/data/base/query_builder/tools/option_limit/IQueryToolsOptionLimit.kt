package gog.kotlin_db.data.base.query_builder.tools.option_limit

import gog.kotlin_db.data.base.query_builder.tools.IQueryTools

interface IQueryToolsOptionLimit: IQueryTools {

    fun setOptionLimit(optionLimit: Int) : QueryToolsOptionLimit;

}