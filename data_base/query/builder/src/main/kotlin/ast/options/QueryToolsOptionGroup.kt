package gog.my_project.data_base.query.builder.ast.options

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.column.QueryToolsColumnsBase
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionGroup
import gog.my_project.data_base.query.definition.interfaces.column.IQueryDefinitionColumnsBase
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionGroup

class QueryToolsOptionGroup(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsOptionGroup , IQueryDefinitionOptionGroup
{

    protected var _groupByList: MutableList<IQueryToolsColumnsBase> = mutableListOf();



    /* ==============================================================
    template
    ============================================================== */
    override fun getListColumns(): MutableList<IQueryToolsColumnsBase> {
        return _groupByList;
    }




    /* ==============================================================
    structure
    ============================================================== */
    override fun addColumn(blockColumn: IQueryDefinitionColumnsBase.() -> IQueryDefinitionColumnsBase): IQueryDefinitionOptionGroup {
        val builder = QueryToolsColumnsBase(params);
        val column = builder.blockColumn();
        _groupByList.add(column as IQueryToolsColumnsBase)
        return this;
    }



}