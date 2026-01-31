package gog.my_project.data_base.query.builder.interfaces.conditions

import gog.my_project.data_base.query.builder.interfaces.IQueryTools

interface IQueryToolsIsConditions : IQueryTools {

    fun setIsAddLogical(isAddLogical: Boolean=false);
    fun isAddLogical(): Boolean;
}