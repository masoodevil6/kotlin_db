package gog.my_project.data_base.query_builder.query.interfaces.with

import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools

interface IQueryToolsWithItem : IQueryTools {

    fun getWithName(): String?;
    fun getWithBody(): IQueryBuilder?;

    fun withName(withName : String): IQueryToolsWithItem;

    fun withBody(blockWith: IQueryBuilder.() -> IQueryBuilder): IQueryToolsWithItem

}