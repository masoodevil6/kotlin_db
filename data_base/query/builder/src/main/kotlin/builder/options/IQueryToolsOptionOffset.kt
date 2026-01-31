package gog.my_project.data_base.query.builder.interfaces.options

import gog.my_project.data_base.query.builder.interfaces.IQueryTools

interface IQueryToolsOptionOffset: IQueryTools {

    fun getOptionOffset() : Long?

}