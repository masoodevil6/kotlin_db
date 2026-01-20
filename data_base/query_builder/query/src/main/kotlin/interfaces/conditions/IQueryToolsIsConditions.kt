package gog.my_project.data_base.query_builder.query.interfaces.conditions

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools

interface IQueryToolsIsConditions : IQueryTools {

    fun setIsAddLogical(isAddLogical: Boolean=false);
    fun isAddLogical(): Boolean;
}