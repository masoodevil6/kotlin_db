package gog.my_project.data_base.query.builder.ast.table

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query.definition.interfaces.table.IQueryDefinitionTable
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class QueryToolsTable(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsTable , IQueryDefinitionTable
{

    /*
    var _tableQuery : IQueryBuilder? = null;

    *//* ==============================================================
    template
    ============================================================== *//*

    override fun getTableQuery(): IQueryBuilder? {
        return _tableQuery;
    }

    *//* ==============================================================
    structure
    ============================================================== *//*

    override fun tableQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsTable {
        val builder = QueryBuilder(params);
        this._tableQuery = builder.block();
        return this;
    }
    */




    var _table : QBTable? = null;
    var _cte :   QBCte? = null;

    /* ==============================================================
       template
    ============================================================== */
    override fun getTable(): QBTable? {
        return _table;
    }

    override fun getCte(): QBCte? {
        return _cte;
    }


    /* ==============================================================
      structure
     ============================================================== */
    override fun <T : IModel> table(table: KClass<T>): IQueryDefinitionTable {
        this._table = table.findAnnotation<QBTable>();
        return this;
    }

    override fun <T : ICte> cte(cte: KClass<T>): IQueryDefinitionTable {
        this._cte = cte.findAnnotation<QBCte>();
        return this;
    }


}