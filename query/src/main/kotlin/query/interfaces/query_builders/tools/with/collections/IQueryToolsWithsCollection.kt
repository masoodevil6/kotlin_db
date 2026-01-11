package gog.my_project.query.interfaces.query_builders.tools.with.collections

import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem

interface IQueryToolsWithsCollection : IQueryTools {

    fun getListWiths(): MutableList<IQueryToolsWithItem>;

    fun addWith(blockWith: IQueryToolsWithItem.() -> IQueryToolsWithItem) : IQueryToolsWithsCollection;

}