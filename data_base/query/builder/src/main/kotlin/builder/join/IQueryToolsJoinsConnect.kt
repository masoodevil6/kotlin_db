package gog.my_project.data_base.query.builder.interfaces.join

import gog.my_project.data_base.query.builder.interfaces.IQueryTools

interface IQueryToolsJoinsConnect :  IQueryTools {

    fun getListJoins(): List<IQueryToolsJoinsItem>

}