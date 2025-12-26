package gog.my_project.query.interfaces.query_builders.tools.conditions

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools


interface IQueryToolsIsConditions : IQueryTools {
    fun toWhereSql(isAddLogical: Boolean=false): String?;
}