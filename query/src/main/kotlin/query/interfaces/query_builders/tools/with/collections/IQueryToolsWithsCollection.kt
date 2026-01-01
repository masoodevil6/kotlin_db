package gog.my_project.query.interfaces.query_builders.tools.with.collections

import gog.my_project.query.interfaces.query_builders.tools.with.item.IQueryToolsWithItem

interface IQueryToolsWithsCollection : IQueryToolsWithsCollectionParams {

    fun addWith(blockWith: IQueryToolsWithItem.() -> IQueryToolsWithItem) : IQueryToolsWithsCollection;

}