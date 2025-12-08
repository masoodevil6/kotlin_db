package gog.kotlin_db.data.base.QueryBuilder.tools.with

import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools

interface IQueryTools_withsCollection : IQueryTools {

    fun setupWiths(blockWiths: (IQueryTools_withsCollection) -> QueryTools_withsCollection): QueryTools_withsCollection;

    fun with(withName: String ,  blockQuery: (QueryBuilder) -> QueryBuilder) : IQueryTools_withsCollection;

}