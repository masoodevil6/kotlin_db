package gog.my_project.data_base.query.builder.interfaces.columns

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.ctes.QBCteSelect
import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.query.builder.interfaces.IQueryTools


interface IQueryToolsColumnsBase : IQueryTools {

    fun getTable():  QBTable?;
    fun getColumn(): QBColumn?;

    fun getCte():    QBCte?;
    fun getSelect(): QBCteSelect?;

}

