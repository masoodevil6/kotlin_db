package gog.kotlin_db.data.base.QueryBuilder.tools.conditions

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools

interface IQueryTools_isConditions : IQueryTools{
    fun toWhereSql(isAddLogical: Boolean=false): String?;
}