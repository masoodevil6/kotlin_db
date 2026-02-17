package gog.my_project.data_base.query.api.interfaces.cte

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.query_render_select.IQueryRenderSelectApi

interface ICte {

    fun cteQuery(params: MutableList<SqlParameter<*>>): IQueryRenderSelectApi

}