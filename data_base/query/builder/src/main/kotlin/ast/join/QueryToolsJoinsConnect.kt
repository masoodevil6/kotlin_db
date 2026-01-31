package gog.my_project.data_base.query.builder.ast.join

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query.builder.interfaces.join.IQueryToolsJoinsItem
import gog.my_project.data_base.query.definition.interfaces.join.IQueryDefinitionJoinsConnect
import gog.my_project.data_base.query.definition.interfaces.join.IQueryDefinitionJoinsItem

class QueryToolsJoinsConnect(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsJoinsConnect , IQueryDefinitionJoinsConnect
{

    protected var joins: MutableList<IQueryToolsJoinsItem> = mutableListOf()



    /* ==============================================================
    template
    ============================================================== */
    override fun getListJoins(): List<IQueryToolsJoinsItem> {
        return joins;
    }



    /* ==============================================================
    structure
    ============================================================== */
    override fun addJoin(blockJoin: IQueryDefinitionJoinsItem.() -> IQueryDefinitionJoinsItem): IQueryDefinitionJoinsConnect {
        val builder = QueryToolsJoinsItem(params);
        joins.add(builder.blockJoin() as IQueryToolsJoinsItem);
        return this;
    }



}