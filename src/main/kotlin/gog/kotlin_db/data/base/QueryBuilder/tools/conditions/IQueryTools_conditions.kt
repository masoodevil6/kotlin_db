package gog.kotlin_db.data.base.QueryBuilder.tools.conditions

interface IQueryTools_conditions {
    fun toWhereSql(isAddLogical: Boolean=false): String?;
}