package gog.kotlin_db.data.base.query_builder.tools.with

import gog.kotlin_db.data.base.query_builder.QueryBuilder
import gog.kotlin_db.data.base.query_builder.tools.IQueryTools

interface IQueryToolsWithsCollection : IQueryTools {

    fun setupWiths(blockWiths: (IQueryToolsWithsCollection) -> QueryToolsWithsCollection): QueryToolsWithsCollection;

    fun with(withName: String ,  blockQuery: (QueryBuilder) -> QueryBuilder) : QueryToolsWithsCollection;

}