package gog.my_project.data_base.query.builder.ast.column

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.ctes.QBCteSelect
import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumnsBase
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

class QueryToolsColumnsBase(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsColumnsBase , IQueryDefinitionColumnsBase
{


    private var table:     QBTable? = null;
    private var column:    QBColumn? = null;

    private var cte:       QBCte? = null;
    private var select:    QBCteSelect? = null;


    /* ==============================================================
        template
    ============================================================== */

    /// table
    override fun getTable(): QBTable? {
        return table;
    }

    override fun getColumn(): QBColumn? {
        return column;
    }


    /// cte
    override fun getCte(): QBCte? {
        return cte;
    }

    override fun getSelect(): QBCteSelect? {
        return select;
    }


    /* ==============================================================
        structure
    ============================================================== */

    override fun <T : IModel, R> column(
        table: KClass<T>,
        column: KProperty1<T, R>
    ): IQueryDefinitionColumnsBase {
        this.table =    table.findAnnotation<QBTable>();
        this.column =   column.findAnnotation<QBColumn>();
        return this;
    }

    override fun <T : ICte, R> selector(
        cte: KClass<T>,
        select: KProperty1<T, R>
    ): IQueryDefinitionColumnsBase {
        this.cte =      cte.findAnnotation<QBCte>();
        this.select =   select.findAnnotation<QBCteSelect>();
        return this;
    }


}