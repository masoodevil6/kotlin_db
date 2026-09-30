package gog.my_project.data_base.query.dialect.data_class.select_data.withs

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass

data class QueryWithsData(
    val _dummy: Int = 0 ,
    val _withPrefix: Boolean = true ,
): QueryDataClass()
