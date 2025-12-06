package gog.kotlin_db.data.base.QueryBuilder.tools.options.OptionOrder

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools

interface IQueryTools_optionOrder: IQueryTools {

    fun addColumnOrder(columnName : String);
    fun setTypeOrder(orderType : String);


}