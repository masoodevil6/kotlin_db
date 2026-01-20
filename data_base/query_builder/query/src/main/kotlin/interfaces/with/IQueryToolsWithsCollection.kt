package gog.my_project.data_base.query_builder.query.interfaces.with

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools

interface IQueryToolsWithsCollection : IQueryTools {

    fun getListWiths(): MutableList<IQueryToolsWithItem>;

    fun addWith(blockWith: IQueryToolsWithItem.() -> IQueryToolsWithItem) : IQueryToolsWithsCollection;

}