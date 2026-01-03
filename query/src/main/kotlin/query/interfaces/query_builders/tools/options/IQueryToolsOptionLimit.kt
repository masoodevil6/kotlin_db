package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsOptionLimit: IQueryTools {

    var params: MutableList<Any?>

    fun getOptionLimit(): Long?

    fun setOptionLimit(optionLimit: Long) : IQueryToolsOptionLimit;

}