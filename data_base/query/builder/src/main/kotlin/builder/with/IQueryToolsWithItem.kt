package gog.my_project.data_base.query.builder.interfaces.with

import gog.my_project.data_base.query.builder.interfaces.IQueryBuilder
import gog.my_project.data_base.query.builder.interfaces.IQueryTools

interface IQueryToolsWithItem : IQueryTools {

    fun getWithName(): String?;
    fun getWithBody(): IQueryBuilder?;

}