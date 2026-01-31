package gog.my_project.data_base.query.builder.interfaces.with

import gog.my_project.data_base.query.builder.interfaces.IQueryTools

interface IQueryToolsWithsCollection :  IQueryTools {

    fun getListWiths(): MutableList<IQueryToolsWithItem>;

}