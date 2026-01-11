package gog.my_project.query.interfaces.query_builders.tools.with.item

import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsWithItem : IQueryTools {

    fun getWithName(): String?;
    fun getWithBody(): IQueryBuilder?;

    fun withName(withName : String): IQueryToolsWithItem;

    fun withBody(blockWith: IQueryBuilder.() -> IQueryBuilder): IQueryToolsWithItem;
}