package gog.my_project.data_base.query.builder.ast.where

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.conditions.QueryToolsConditionsGroups
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query.builder.interfaces.where.IQueryToolsWhere
import gog.my_project.data_base.query.definition.interfaces.conditions.IQueryDefinitionConditionsGroups
import gog.my_project.data_base.query.definition.interfaces.where.IQueryDefinitionWhere

class QueryToolsWhere(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsWhere , IQueryDefinitionWhere
{


    var condition: IQueryToolsConditionsGroups? = null;

    /* ==============================================================
    template
    ============================================================== */
    override fun getGroupCondition(): IQueryToolsConditionsGroups? {
        return condition;
    }



    /* ==============================================================
    structure
    ============================================================== */
    override fun conditions(blockCondition: IQueryDefinitionConditionsGroups.() -> IQueryDefinitionConditionsGroups): IQueryDefinitionWhere {
        val builder = QueryToolsConditionsGroups(params);
        condition = builder.blockCondition() as IQueryToolsConditionsGroups?;
        return this;
    }




}