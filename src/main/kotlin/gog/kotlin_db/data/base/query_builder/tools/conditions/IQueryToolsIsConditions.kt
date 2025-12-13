package gog.kotlin_db.data.base.query_builder.tools.conditions

import gog.kotlin_db.data.base.query_builder.tools.IQueryTools

interface IQueryToolsIsConditions : IQueryTools{
    fun toWhereSql(isAddLogical: Boolean=false): String?;
}