package gog.kotlin_db.data.base.QueryBuilder


import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroup
import gog.kotlin_db.data.base.QueryBuilder.tools.options.IQueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.where.IQueryTools_where
import gog.kotlin_db.data.base.QueryBuilder.tools.where.QueryTools_where

interface IQueryBuilder: IQueryTools {


    fun where(blockGroup: (QueryTools_conditionsGroup) -> QueryTools_conditionsGroup): QueryBuilder;
    fun options(blockGroup: (IQueryTools_options) -> QueryTools_options): QueryBuilder;



}