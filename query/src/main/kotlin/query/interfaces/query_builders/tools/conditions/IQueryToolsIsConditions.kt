package gog.my_project.query.interfaces.query_builders.tools.conditions

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools


interface IQueryToolsIsConditions : IQueryTools {
    fun setIsAddLogical(isAddLogical: Boolean=false): String?;
    fun isAddLogical(): Boolean;
}