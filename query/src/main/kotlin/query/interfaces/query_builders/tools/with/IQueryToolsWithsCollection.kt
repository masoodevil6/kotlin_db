package gog.my_project.query.interfaces.query_builders.tools.with

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsWithsCollection : IQueryTools {

    fun setupWiths(blockWiths: (IQueryToolsWithsCollection) -> IQueryToolsWithsCollection): IQueryToolsWithsCollection;

    fun with(withName: String ,  blockQuery: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsWithsCollection;

}