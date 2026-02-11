package gog.my_project.data_base.query.dialect.data_class.select

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass

data class QuerySelectData(
    val _dummy: Int = 0 ,
    val _withPrefix: Boolean = true,
): QueryDataClass()
