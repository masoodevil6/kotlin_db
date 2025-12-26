package gog.my_project.query.interfaces.query_builders.tools.select


import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import kotlin.reflect.KProperty

interface IQueryToolsSelect : IQueryTools {

    fun selectSetup(blockSelect: (IQueryToolsSelect) -> IQueryToolsSelect): IQueryToolsSelect;

    fun column(columnsName: String) : IQueryToolsSelect;
    fun column(columnsName: String , columnAlias: String) : IQueryToolsSelect;
    fun column(columnAlias: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect;

    fun column(prop: KProperty<*>) : IQueryToolsSelect;

    fun count(columnsName: String, columnAlias: String = "count") : IQueryToolsSelect;
    fun count(columnAlias: String = "count", block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect;

    fun sum(columnsName: String, columnAlias: String = "sum") : IQueryToolsSelect;
    fun sum(columnAlias: String = "sum", block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect;

    fun avg(columnsName: String, columnAlias: String = "avg") : IQueryToolsSelect;
    fun avg(columnAlias: String = "avg" , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect;

    fun max(columnsName: String, columnAlias: String = "max") : IQueryToolsSelect;
    fun max(columnAlias: String = "max" , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect;

    fun min(columnsName: String, columnAlias: String = "min") : IQueryToolsSelect;
    fun min(columnAlias: String = "min" , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect;

    fun methodFun(methodName: String, columnsName: String, columnAlias: String) : IQueryToolsSelect;
    fun methodFun(methodName: String, columnAlias: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect;

}