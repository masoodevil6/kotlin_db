package gog.my_project.query.query_builder.tools.conditions

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsIsConditions
import gog.my_project.query.query_builder.QueryBuilder


class QueryToolsConditionsGroups(
    val conditionLogical: String,
):
    IQueryToolsConditionsGroups
{

    public var isAddLogical: Boolean = false;

    protected var conditions: MutableList<IQueryToolsIsConditions> = mutableListOf()










    override fun group(
        conditionLogical: String ,
        block: (IQueryToolsConditionsGroups) -> IQueryToolsIsConditions
    ): IQueryToolsConditionsGroups {
        val conditionSchema = block(QueryToolsConditionsGroups(conditionLogical));
        conditions.add(conditionSchema);
        return this;
    }










    override fun whereAnd(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, conditionOperation, sideRight);
    }

    override fun whereAnd(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, conditionOperation, block);
    }

    override fun whereOn(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_ON, sideLeft, conditionOperation, sideRight);
    }

    override fun  whereOn(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_ON, sideLeft, conditionOperation, block);
    }

    override fun whereOr(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_OR, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOr(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_OR, sideLeft, conditionOperation, block);
    }


    override fun whereIn(sideLeft: String? , listValues: List<String>): IQueryToolsConditionsGroups {
        if (listValues.size > 0){
            var sideRight = " (";
            for ((index, value) in listValues.withIndex()){
                sideRight +=  " $sideRight";
                if (index < listValues.size - 1){
                    sideRight += ","
                }
            }
            sideRight += ") "
            return whereCondition(_LOGICAL_AND, sideLeft, _OPERATION_IN, sideRight);
        }
        return this;
    }

    override fun whereIn(sideLeft: String? , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, _OPERATION_IN, block);
    }



    override fun whereLike(sideLeft: String? , search: String? , conditionLogical: String) : IQueryToolsConditionsGroups {
        if (search != null && search.isNotEmpty()) {
            group(conditionLogical){
                    schema->
                schema
                    .whereCondition(_LOGICAL_AND , sideLeft , _OPERATION_LIKE , "'$search'")
                    .whereCondition(_LOGICAL_AND , sideLeft , _OPERATION_LIKE , "'%$search'")
                    .whereCondition(_LOGICAL_AND , sideLeft , _OPERATION_LIKE , "'$search%'")
                    .whereCondition(_LOGICAL_AND , sideLeft , _OPERATION_LIKE , "'%$search%'")
            }
        }
        return this;
    }



    override fun whereNull(conditionLogical: String ,  sideLeft: String): IQueryToolsConditionsGroups {
        return whereCondition(conditionLogical, sideLeft, "", " is null ");
    }

    override fun whereNull(conditionLogical: String, block: (IQueryBuilder) -> IQueryBuilder, conditionOperation: String): IQueryToolsConditionsGroups {
        val sideLeft = block(QueryBuilder());
        return whereCondition(conditionLogical, "(${sideLeft.toSql().toString()})" , "", " is null ");
    }




    override fun whereCondition(conditionLogical: String , sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups {
        val condition = QueryToolsConditions(conditionLogical, sideLeft, conditionOperation, sideRight);
        conditions.add(condition);
        return this;
    }

    override fun whereCondition(conditionLogical: String ,sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups {
        val sideRight = block(QueryBuilder());
        val condition =
            QueryToolsConditions(conditionLogical, sideLeft, conditionOperation, "(${sideRight?.toSql().toString()})");
        conditions.add(condition);
        return this;
    }








    override fun toWhereSql(isAddLogical: Boolean): String? {
        this.isAddLogical = isAddLogical;
        return toSql();
    }





    companion object {

        /*----------------
           Logical
        ----------------*/
        const val _LOGICAL_AND =   "and"
        const val _LOGICAL_ON =    "on"
        const val _LOGICAL_OR  =   "or"


        /*----------------
           Operation
        ----------------*/
        const val _OPERATION_EQUALS =               "="
        const val _OPERATION_NOT_EQUALS =           "<>"
        const val _OPERATION_GEATER_THAN =          ">"
        const val _OPERATION_LESS_THAN =            "<"
        const val _OPERATION_GEATER_OR_EQUAL_THAN = ">="
        const val _OPERATION_LESS_OR_EQUAL_THAN =   "<="
        const val _OPERATION_LIKE =                 "like"
        const val _OPERATION_IN =                   "IN"
        const val _OPERATION_BETWEEN =              "between"
    }



    override fun getBaseTempSql(): String? {
        return "";
    }

    override fun toSql(): String? {
        return DatabaseConfig.dbTypeName.getConditionGroupSql(conditionLogical , conditions , isAddLogical);
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }




}