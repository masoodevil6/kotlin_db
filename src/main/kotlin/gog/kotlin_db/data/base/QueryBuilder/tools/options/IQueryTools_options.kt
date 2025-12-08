package gog.kotlin_db.data.base.QueryBuilder.tools.options

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools

interface IQueryTools_options : IQueryTools{

    fun optionsSetup(blockGroup: (IQueryTools_options) -> QueryTools_options): QueryTools_options;

    fun addGroup(columnName: String) : QueryTools_options;

    fun addOrder(columnName: String) : QueryTools_options;
    fun orderType(orderType: String) : QueryTools_options;

    fun pageInit(pageLimit: Int , pageOffset: Int): QueryTools_options
    fun pageLimit(pageLimit: Int): QueryTools_options
    fun pageOffset(pageOffset: Int): QueryTools_options


}