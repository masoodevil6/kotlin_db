package gog.kotlin_db.data.base.query_builder.tools.option_group

import gog.kotlin_db.data.base.query_builder.tools.IQueryTools

interface IQueryToolsOptionGroup : IQueryTools{

    fun groupSetup(blockGroup: (IQueryToolsOptionGroup) -> QueryToolsOptionGroup) : QueryToolsOptionGroup;

    fun addColumn(columnName : String): QueryToolsOptionGroup;

}