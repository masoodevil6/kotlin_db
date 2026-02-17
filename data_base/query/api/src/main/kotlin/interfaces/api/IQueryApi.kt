package gog.my_project.data_base.query.api.interfaces.api

import gog.my_project.data_base.core.query.reader.SqlParameter

interface IQueryApi {

    var params: MutableList<SqlParameter<*>>

}