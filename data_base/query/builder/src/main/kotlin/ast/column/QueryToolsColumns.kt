package gog.my_project.data_base.query.builder.ast.column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumns
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumns
import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumnsBase
import gog.my_project.data_base.query.definition.tools.enums.SqlMethodColumn

class QueryToolsColumns(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsColumns , IQueryDefinitionColumns
{


    private var ColumnMethod: SqlMethodColumn? = null;
    private var ColumnName:   IQueryToolsColumnsBase? = null;
    private var ColumnAlias:  String? = null;





    /* ==============================================================
    template
    ============================================================== */
    override fun getColumnMethod(): SqlMethodColumn? {
        return ColumnMethod;
    }

    override fun getColumnName(): IQueryToolsColumnsBase? {
        return ColumnName;
    }

    override fun getColumnAlias(): String? {
        return ColumnAlias;
    }








    /* ==============================================================
    structure
    ============================================================== */
    override fun method(method: SqlMethodColumn): IQueryDefinitionColumns {
        this.ColumnMethod = method;
        return this;
    }

    override fun sum(): IQueryDefinitionColumns {
        this.ColumnMethod = SqlMethodColumn.Sum;
        return this;
    }

    override fun count(): IQueryDefinitionColumns {
        this.ColumnMethod = SqlMethodColumn.Count;
        return this;
    }

    override fun avg(): IQueryDefinitionColumns {
        this.ColumnMethod = SqlMethodColumn.Avg;
        return this;
    }

    override fun min(): IQueryDefinitionColumns {
        this.ColumnMethod = SqlMethodColumn.Min;
        return this;
    }

    override fun max(): IQueryDefinitionColumns {
        this.ColumnMethod = SqlMethodColumn.Max;
        return this;
    }







    override fun column(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionColumns {
        val builder = QueryToolsColumnsBase(params);
        this.ColumnName = builder.blockColumn() as IQueryToolsColumnsBase?;
        return this;
    }





    override fun alias(alias: String): IQueryDefinitionColumns {
        this.ColumnAlias = alias;
        return this;
    }


}