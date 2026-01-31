package gog.my_project.data_base.query.builder.ast.with

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.with.IQueryToolsWithItem
import gog.my_project.data_base.query.builder.interfaces.with.IQueryToolsWithsCollection
import gog.my_project.data_base.query.definition.interfaces.with.IQueryDefinitionWithsCollection
import gog.my_project.data_base.query.definition.interfaces.with.IQueryDefinitionWithsItem

class QueryToolsWithsCollection(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsWithsCollection , IQueryDefinitionWithsCollection
{


    protected var withs: MutableList<IQueryToolsWithItem> = mutableListOf()


    /* ==============================================================
    template
    ============================================================== */
    override fun getListWiths(): MutableList<IQueryToolsWithItem> {
        return withs;
    }



    /* ==============================================================
    structure
    ============================================================== */
    override fun addWith(blockWith: IQueryDefinitionWithsItem.() -> IQueryDefinitionWithsItem): IQueryDefinitionWithsCollection {
        val builder = QueryToolsWithItem(params);
        withs.add(builder.blockWith() as IQueryToolsWithItem);
        return this;
    }



}