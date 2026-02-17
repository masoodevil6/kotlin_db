package gog.my_project.data_base.query.dialect.data_class.column

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass

data class QueryColumnData(
    val _dummy: Int = 0 ,
    val _withAlias: Boolean = false ,
): QueryDataClass()
