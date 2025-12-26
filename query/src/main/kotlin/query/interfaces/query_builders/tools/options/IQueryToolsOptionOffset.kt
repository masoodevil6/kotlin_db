package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools


interface IQueryToolsOptionOffset: IQueryTools {

    fun setOptionOffset(optionOffset: Int) : IQueryToolsOptionOffset;

}