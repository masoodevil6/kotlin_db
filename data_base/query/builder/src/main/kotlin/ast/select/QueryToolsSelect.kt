package gog.my_project.data_base.query.builder.ast.select

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.column.QueryToolsColumns
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumns
import gog.my_project.data_base.query.builder.interfaces.select.IQueryToolsSelect
import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumns
import gog.my_project.data_base.query.definition.interfaces.select.IQueryDefinitionSelect

class QueryToolsSelect(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsSelect , IQueryDefinitionSelect
{


    protected var columns: MutableList<IQueryToolsColumns> = mutableListOf()



    /* ==============================================================
    template
    ============================================================== */
    override fun getListColumns(): MutableList<IQueryToolsColumns> {
        return columns;
    }






    /* ==============================================================
    structure
    ============================================================== */
    override fun addColumn(blockColumn: IQueryDefinitionColumns.() -> IQueryDefinitionColumns): IQueryDefinitionSelect {
        val builder = QueryToolsColumns(params);
        columns.add(builder.blockColumn() as IQueryToolsColumns);
        return this;
    }





}