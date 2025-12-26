package gog.my_project.query.query_builder.tools.select


import data.base.schemas.QBColumn
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.select.IQueryToolsSelect
import gog.my_project.query.query_builder.QueryBuilder
import gog.my_project.query.query_builder.tools.column.QueryToolsColumns
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

class QueryToolsSelect(

) : IQueryToolsSelect {

    protected var columns: MutableList<IQueryToolsColumns> = mutableListOf()


    companion object {
        const val _TAG_TEMP_SELECT=             "{{_TAG_TEMP_SELECT}}"
        const val _TAG_TEMP_SELECT_COLUMNS=     "{{_TAG_TEMP_SELECT_COLUMNS}}"

        /*----------------
          Methods
        ----------------*/
        const val _QUERY_FUN_COUNT =  "count"
        const val _QUERY_FUN_SUM =    "sum"
        const val _QUERY_FUN_AVG =    "avg"
        const val _QUERY_FUN_MAX =    "max"
        const val _QUERY_FUN_MIN =    "min"
    }







    override fun selectSetup(blockSelect: (IQueryToolsSelect) -> IQueryToolsSelect): IQueryToolsSelect {
        return  blockSelect(QueryToolsSelect());
    }

    override fun column(columnsName: String) : IQueryToolsSelect {
        val column = QueryToolsColumns(columnsName);
        columns?.add(column);
        return this;
    }

    override fun column(columnsName: String , columnAlias: String) : IQueryToolsSelect {
        val column = QueryToolsColumns(columnsName, columnAlias);
        columns?.add(column);
        return this;
    }

    override fun column(columnAlias: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect {
        val columnsName = block(QueryBuilder());
        val column = QueryToolsColumns("(${columnsName.toSql().toString()})", columnAlias);
        columns?.add(column);
        return this;
    }


    override fun column(prop: KProperty<*>) : IQueryToolsSelect {
       val columnName = prop.findAnnotation<QBColumn>()?.name;
        TODO()
    }



    override fun count(columnsName: String, columnAlias: String) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_COUNT , columnsName , columnAlias);
    }

    override fun count(columnAlias: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_COUNT, columnAlias , block);
    }



    override fun sum(columnsName: String, columnAlias: String) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_SUM, columnsName , columnAlias);
    }

    override fun sum(columnAlias: String, block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_SUM, columnAlias , block);
    }



    override fun avg(columnsName: String, columnAlias: String) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_AVG, columnsName , columnAlias);
    }

    override fun avg(columnAlias: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_AVG, columnAlias , block);
    }




    override fun max(columnsName: String, columnAlias: String ) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_MAX, columnsName , columnAlias);
    }

    override fun max(columnAlias: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_MAX, columnAlias , block);
    }




    override fun min(columnsName: String, columnAlias: String) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_MIN, columnsName , columnAlias);
    }

    override fun min(columnAlias: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect {
        return methodFun(_QUERY_FUN_MIN, columnAlias , block);
    }



    override fun methodFun(methodName: String, columnsName: String, columnAlias: String) : IQueryToolsSelect {
        val column = QueryToolsColumns(columnsName, columnAlias, methodName);
        columns?.add(column);
        return this;
    }

    override fun methodFun(methodName: String, columnAlias: String , block: (IQueryBuilder) -> IQueryBuilder) : IQueryToolsSelect {
        val columnsName = block(QueryBuilder());
        val column = QueryToolsColumns("(${columnsName.toSql().toString()})", columnAlias, methodName);
        columns?.add(column);
        return this;
    }










    override fun getBaseTempSql(): String? {
        return "";
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getSelectSql(columns);
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(ObjectSqlTypeTemplates._TAG_TEMP_SELECT, querySelect ?: "");
    }


}