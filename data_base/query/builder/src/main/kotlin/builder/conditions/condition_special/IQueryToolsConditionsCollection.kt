package gog.my_project.data_base.query_builder.query.interfaces.conditions.condition_in

import gog.my_project.data_base.query.builder.interfaces.IQueryTools

interface IQueryToolsConditionsCollection  : IQueryTools {

    fun getParamsCollection(): MutableList<Any>

}