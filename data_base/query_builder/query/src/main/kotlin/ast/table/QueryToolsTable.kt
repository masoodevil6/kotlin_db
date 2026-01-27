package gog.my_project.data_base.query_builder.query.ast.table

import gog.my_project.data_base.annotations.models.QBTable
import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.models.main.BaseModel
import gog.my_project.data_base.query_builder.query.ast.QueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.table.IQueryToolsTable
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation


class QueryToolsTable(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsTable
{

    /*var _tableName : String? = null;
    var _tableAlias : String? = null;
    var _tableQuery : IQueryBuilder? = null;



    *//* ==============================================================
    template
    ============================================================== *//*
    override fun getTableName(): String? {
        return _tableName;
    }

    override fun getTableAlias(): String? {
        return _tableAlias;
    }

    override fun getTableQuery(): IQueryBuilder? {
        return _tableQuery;
    }




    *//* ==============================================================
    Builder
    ============================================================== *//*
    *//*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getTableSql(this);
    }*//*




    *//* ==============================================================
    structure
    ============================================================== *//*
    override fun table(tableName: String?): IQueryToolsTable {
        this._tableName = tableName;
        return this;
    }

    override fun tableQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsTable {
        val builder = QueryBuilder(params);
        this._tableQuery = builder.block();
        return this;
    }





    override fun alias(aliasName: String?): IQueryToolsTable {
        this._tableAlias = aliasName;
        return this;
    }*/




    var _table : QBTable? = null;

    /* ==============================================================
       template
    ============================================================== */
    override fun getTable(): QBTable? {
        return _table;
    }


    /* ==============================================================
      structure
     ============================================================== */
    override fun <T : BaseModel> table(table: KClass<T>): IQueryToolsTable {
        this._table = table.findAnnotation<QBTable>();
        return this;
    }


}