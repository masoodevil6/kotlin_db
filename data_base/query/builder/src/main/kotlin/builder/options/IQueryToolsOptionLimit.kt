package gog.my_project.data_base.query.builder.interfaces.options

import gog.my_project.data_base.query.builder.interfaces.IQueryTools

interface IQueryToolsOptionLimit: IQueryTools {

    fun getOptionLimit(): Long?

}