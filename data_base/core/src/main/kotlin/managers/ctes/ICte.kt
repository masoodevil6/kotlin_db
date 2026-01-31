package gog.my_project.data_base.core.managers.ctes

import gog.my_project.data_base.core.query.reader.SqlParameter


interface ICte {

    fun cteQuery(
        params: MutableList<SqlParameter<*>>
    )/*: IQueryBuilder*/

}