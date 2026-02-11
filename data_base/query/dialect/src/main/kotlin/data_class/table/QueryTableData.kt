package gog.my_project.data_base.query.dialect.data_class.table

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass

data class QueryTableData(
    val _dummy: Int = 0 ,
    val _withPrefix: Boolean = true ,
): QueryDataClass()
