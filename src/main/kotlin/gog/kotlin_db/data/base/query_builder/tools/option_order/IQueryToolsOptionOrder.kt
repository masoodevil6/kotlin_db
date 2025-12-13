package gog.kotlin_db.data.base.query_builder.tools.option_order

import gog.kotlin_db.data.base.query_builder.tools.IQueryTools

interface IQueryToolsOptionOrder: IQueryTools {

    fun orderSetup(blockOrder: (IQueryToolsOptionOrder) -> QueryToolsOptionOrder) : QueryToolsOptionOrder;


    fun addColumn(columnName : String) : QueryToolsOptionOrder;
    fun type(orderType : String) : QueryToolsOptionOrder;


}