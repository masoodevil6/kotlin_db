package gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups

import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.IQueryTools_isConditions
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditions.QueryTools_conditions
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

class QueryTools_conditionsGroups(
    val conditionLogical: String,
):
    IQueryTools_conditionsGroups
{

    public var isAddLogical: Boolean = false;

    protected var conditions: MutableList<IQueryTools_isConditions> = mutableListOf()










    override fun group(
        conditionLogical: String,
        block: (QueryTools_conditionsGroups) -> IQueryTools_isConditions
    ): QueryTools_conditionsGroups {
        val conditionSchema = block(QueryTools_conditionsGroups(conditionLogical));
        conditions.add(conditionSchema);
        return this;
    }










    override fun whereAnd(
        sideLeft: String?,
        conditionOperation: String,
        sideRight: String?
    ): QueryTools_conditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, conditionOperation, sideRight);
    }

    override fun whereAnd(
        sideLeft: String?,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryTools_conditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, conditionOperation, block);
    }

    override fun whereOn(
        sideLeft: String?,
        conditionOperation: String,
        sideRight: String?
    ): QueryTools_conditionsGroups {
        return whereCondition(_LOGICAL_ON, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOn(
        sideLeft: String?,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryTools_conditionsGroups {
        return whereCondition(_LOGICAL_ON, sideLeft, conditionOperation, block);
    }

    override fun whereOr(
        sideLeft: String?,
        conditionOperation: String,
        sideRight: String?
    ): QueryTools_conditionsGroups {
        return whereCondition(_LOGICAL_OR, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOr(
        sideLeft: String?,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryTools_conditionsGroups {
        return whereCondition(_LOGICAL_OR, sideLeft, conditionOperation, block);
    }


    override fun whereIn(
        sideLeft: String?,
        listValues: List<String>
    ): QueryTools_conditionsGroups {
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

    override fun whereIn(
        sideLeft: String?,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryTools_conditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, _OPERATION_IN, block);
    }



    override fun whereLike(
        sideLeft: String?,
        search: String? ,
        conditionLogical: String
    ): QueryTools_conditionsGroups {
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



    override fun whereNull(
        conditionLogical: String,
        sideLeft: String
    ): QueryTools_conditionsGroups {
        return whereCondition(conditionLogical, sideLeft, "", " is null ");
    }

    override fun whereNull(
        conditionLogical: String,
        block: (QueryBuilder) -> QueryBuilder,
        conditionOperation: String
    ): QueryTools_conditionsGroups {
        val sideLeft = block(QueryBuilder());
        return whereCondition(conditionLogical, "(${sideLeft.toSql().toString()})" , "", " is null ");
    }




    override fun whereCondition(
        conditionLogical: String,
        sideLeft: String?,
        conditionOperation: String,
        sideRight: String?
    ): QueryTools_conditionsGroups {
        val condition = QueryTools_conditions(conditionLogical, sideLeft, conditionOperation, sideRight);
        conditions.add(condition);
        return this;
    }

    override fun whereCondition(
        conditionLogical: String,
        sideLeft: String?,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryTools_conditionsGroups {
        val sideRight = block(QueryBuilder());
        val condition = QueryTools_conditions(conditionLogical, sideLeft, conditionOperation, "(${sideRight?.toSql().toString()})");
        conditions.add(condition);
        return this;
    }








    override fun toWhereSql(isAddLogical: Boolean): String? {
        this.isAddLogical = isAddLogical;
        return toSql();
    }





    companion object {

        /*----------------
          Templates
        ----------------*/
        const val _TAG_TEMP_CONDITION=             "{{_TAG_TEMP_CONDITION}}"
        const val _TAG_TEMP_CONDITION_LOGICAL=     "{{_TAG_TEMP_CONDITION_LOGICAL}}"
        const val _TAG_TEMP_CONDITION_GROUP=        "{{_TAG_TEMP_CONDITION_LEFT}}"

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
        return " $_TAG_TEMP_CONDITION_LOGICAL ($_TAG_TEMP_CONDITION_GROUP)"
    }

    override fun toSql(): String? {
        if (conditions != null && conditions.size>0) {

            var queryTemp = getBaseTempSql();
            queryTemp =  queryTemp?.replace(_TAG_TEMP_CONDITION_LOGICAL, if(isAddLogical){conditionLogical} else{""});

            var conditionStr = "";
            for ((index, condition) in conditions.withIndex()){
                conditionStr += condition.toWhereSql(index > 0).toString()
            }
            queryTemp =  queryTemp?.replace(_TAG_TEMP_CONDITION_GROUP, conditionStr);

            return queryTemp;
        }
        return "";
    }

    override fun replaceInBaseTemp(query: String): String {
        return toSql() ?: "";
    }




}