package gog.my_project.data_base.query.builder.interfaces.table

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.query.builder.interfaces.IQueryTools

interface IQueryToolsTable : IQueryTools {

    fun getTable(): QBTable?;
    fun getCte():   QBCte?;

}