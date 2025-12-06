package gog.kotlin_db.data.base.QueryBuilder.Conditions

interface IQueryBuilder_conditions {
    fun toWhereSql(isAddLogical: Boolean=false): String?;
}