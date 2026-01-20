package gog.my_project.data_base.query_builder.query.interfaces.options

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools


interface IQueryToolsOptionOffset: IQueryTools {

    fun getOptionOffset() : Long?

    fun setOptionOffset(optionOffset: Long) : IQueryToolsOptionOffset;

}