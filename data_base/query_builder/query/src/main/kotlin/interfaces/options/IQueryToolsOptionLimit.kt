package gog.my_project.data_base.query_builder.query.interfaces.options

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools


interface IQueryToolsOptionLimit: IQueryTools {

    fun getOptionLimit(): Long?

    fun setOptionLimit(optionLimit: Long) : IQueryToolsOptionLimit;

}