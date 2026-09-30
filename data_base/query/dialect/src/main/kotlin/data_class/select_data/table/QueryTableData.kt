package gog.my_project.data_base.query.dialect.data_class.select_data.table

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass

data class QueryTableData(
    val _dummy: Int = 0 ,
    val _withAlias: Boolean = true ,
    val _withPrefix: Boolean = true ,
): QueryDataClass()
