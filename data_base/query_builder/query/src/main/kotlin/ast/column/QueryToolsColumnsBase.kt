package gog.my_project.data_base.query_builder.query.ast.column

import gog.my_project.data_base.annotations.models.QBColumn
import gog.my_project.data_base.annotations.models.QBTable
import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.models.main.BaseModel
import gog.my_project.data_base.query_builder.query.ast.QueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

class QueryToolsColumnsBase(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsColumnsBase
{


    /*private var columnPrefix: String? = null;
    private var columnName:   String? = null;
    private var columnQuery:  IQueryBuilder? = null;



    *//* ==============================================================
    template
    ============================================================== *//*
    override fun getColumnPrefix(): String? {
        return columnPrefix;
    }

    override fun getColumnName(): String? {
        return columnName;
    }

    override fun getColumnQuery(): IQueryBuilder? {
        return columnQuery;
    }




    *//* ==============================================================
    structure
    ============================================================== *//*
    override fun columnPrefix(columnPrefix: String): IQueryToolsColumnsBase {
        this.columnPrefix = columnPrefix;
        return this;
    }*/

    /*override fun columnName(columnName: String): IQueryToolsColumnsBase {
        this.columnName = columnName;
        return this;
    }*/





    private var table:     QBTable? = null;
    private var column:    QBColumn? = null;


    /* ==============================================================
        template
    ============================================================== */
    override fun getTable(): QBTable? {
        return table;
    }

    override fun getColumn(): QBColumn? {
        return column;
    }



    /* ==============================================================
        structure
    ============================================================== */

    override fun <T : BaseModel , R> column(table: KClass<T> , column: KProperty1<T, R>): IQueryToolsColumnsBase {
        this.table =    table.findAnnotation<QBTable>();
        this.column =   column.findAnnotation<QBColumn>();
        return this;
    }


}