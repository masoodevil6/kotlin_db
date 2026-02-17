package gog.my_project.data_base.query.builder.interfaces.builder

import gog.my_project.data_base.core.query.reader.SqlParameter

interface IQuery {

    var params: MutableList<SqlParameter<*>>

}