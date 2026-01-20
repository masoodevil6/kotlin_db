package gog.my_project.data_base.query_builder.query.interfaces.columns

import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools

interface IQueryToolsColumnsBase : IQueryTools {

    fun getColumnPrefix(): String?;
    fun getColumnName(): String?;
    fun getColumnQuery(): IQueryBuilder?;

    fun columnPrefix(columnPrefix: String): IQueryToolsColumnsBase;
    fun columnName(columnName: String): IQueryToolsColumnsBase;
    fun columnQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsColumnsBase;


}

