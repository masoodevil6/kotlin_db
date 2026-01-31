package gog.my_project.data_base.query.builder.interfaces

import gog.my_project.data_base.core.query.reader.SqlParameter

interface IQueryTools {

    var params: MutableList<SqlParameter<*>>

}