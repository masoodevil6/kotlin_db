package gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionPageInit

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools

interface IQueryTools_optionPageInit: IQueryTools {

    fun setOptionPageInit_limit(pageLimit: Int);

    fun setOptionPageInit_offset(pageOffset: Int);

}