package gog.my_project.data_base.query.builder.ast.conditions

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsIsConditions
import gog.my_project.data_base.query.definition.interfaces.conditions.IQueryDefinitionConditions
import gog.my_project.data_base.query.definition.interfaces.conditions.IQueryDefinitionConditionsGroups
import gog.my_project.data_base.query.definition.tools.enums.SqlLogical

open class QueryToolsConditionsGroups(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
):
    IQueryToolsConditionsGroups , IQueryDefinitionConditionsGroups
{

    private var isAddLogical:     Boolean = false;
    private var conditionLogical: SqlLogical? = SqlLogical.And;
    private var conditions:       MutableList<IQueryToolsIsConditions> = mutableListOf()





    /* ==============================================================
    template
    ============================================================== */
    override fun getGroupLogical(): SqlLogical? {
        return conditionLogical;
    }

    override fun getGroupConditions(): MutableList<IQueryToolsIsConditions> {
        return conditions;
    }

    override fun isAddLogical(): Boolean {
        return this.isAddLogical;
    }




    override fun setIsAddLogical(isAddLogical: Boolean){
        this.isAddLogical = isAddLogical;
    }





    /* ==============================================================
    structure
    ============================================================== */

    override fun logical(logical: SqlLogical): IQueryDefinitionConditionsGroups {
        this.conditionLogical = logical;
        return this;
    }

    override fun logicalAnd(): IQueryDefinitionConditionsGroups {
        return this.logical(SqlLogical.And)
    }

    override fun logicalOr(): IQueryDefinitionConditionsGroups {
        return this.logical(SqlLogical.Or)
    }

    override fun logicalOn(): IQueryDefinitionConditionsGroups {
        return this.logical(SqlLogical.On)
    }




    override fun addGroup(
        blockGroup: IQueryDefinitionConditionsGroups.() -> IQueryDefinitionConditionsGroups
    ): IQueryDefinitionConditionsGroups {
        val builder =
            QueryToolsConditionsGroups(params);
        val conditionSchema = builder.blockGroup();
        conditions.add(conditionSchema as IQueryToolsIsConditions);
        return this;
    }



    override fun addCondition(
        blockCondition: IQueryDefinitionConditions.() -> IQueryDefinitionConditions
    ): IQueryDefinitionConditionsGroups {
        val builder =
            QueryToolsConditions(params);
        val conditionSchema = builder.blockCondition();
        conditions.add(conditionSchema as IQueryToolsIsConditions);
        return this;
    }



}