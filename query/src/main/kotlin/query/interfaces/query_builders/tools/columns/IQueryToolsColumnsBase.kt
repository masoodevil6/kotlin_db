package gog.my_project.query.interfaces.query_builders.tools.columns

import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsColumnsBase : IQueryTools {

    fun getColumnPrefix(): String?;
    fun getColumnName(): String?;
    fun getColumnQuery(): IQueryBuilder?;

    fun columnPrefix(columnPrefix: String): IQueryToolsColumnsBase;
    fun columnName(columnName: String): IQueryToolsColumnsBase;
    fun columnQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsColumnsBase;


}

