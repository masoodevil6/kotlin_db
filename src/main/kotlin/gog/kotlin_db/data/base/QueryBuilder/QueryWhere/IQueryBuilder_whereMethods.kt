package gog.kotlin_db.data.base.QueryBuilder.QueryWhere

import gog.kotlin_db.data.base.QueryBuilder.IQueryBuilderMethods
import gog.kotlin_db.data.base.QueryBuilder.Conditions.QueryConditionGroups.QueryBuilder_conditionsGroup
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

interface IQueryBuilder_whereMethods : IQueryBuilderMethods{

    fun where(blockGroup: (QueryBuilder_conditionsGroup) -> QueryBuilder_conditionsGroup): QueryBuilder;

}