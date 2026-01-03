package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools


interface IQueryToolsOptionOffset: IQueryTools {

    var params: MutableList<Any?>

    fun getOptionOffset() : Long?

    fun setOptionOffset(optionOffset: Long) : IQueryToolsOptionOffset;

}