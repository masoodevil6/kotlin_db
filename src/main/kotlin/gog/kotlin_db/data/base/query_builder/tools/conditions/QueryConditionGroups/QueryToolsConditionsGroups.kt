package gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.data.base.query_builder.tools.conditions.IQueryToolsIsConditions
import gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditions.QueryToolsConditions
import gog.kotlin_db.data.base.query_builder.QueryBuilder
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates

class QueryToolsConditionsGroups(
    val conditionLogical: String,
):
    IQueryToolsConditionsGroups
{

    public var isAddLogical: Boolean = false;

    protected var conditions: MutableList<IQueryToolsIsConditions> = mutableListOf()










    override fun group(
        conditionLogical: String,
        block: (QueryToolsConditionsGroups) -> IQueryToolsIsConditions
    ): QueryToolsConditionsGroups {
        val conditionSchema = block(QueryToolsConditionsGroups(conditionLogical));
        conditions.add(conditionSchema);
        return this;
    }










    override fun whereAnd(
        sideLeft: String?,
        conditionOperation: String,
        sideRight: String?
    ): QueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, conditionOperation, sideRight);
    }

    override fun whereAnd(
        sideLeft: String?,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, conditionOperation, block);
    }

    override fun whereOn(
        sideLeft: String?,
        conditionOperation: String,
        sideRight: String?
    ): QueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_ON, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOn(
        sideLeft: String?,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_ON, sideLeft, conditionOperation, block);
    }

    override fun whereOr(
        sideLeft: String?,
        conditionOperation: String,
        sideRight: String?
    ): QueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_OR, sideLeft, conditionOperation, sideRight);
    }

    override fun whereOr(
        sideLeft: String?,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_OR, sideLeft, conditionOperation, block);
    }


    override fun whereIn(
        sideLeft: String?,
        listValues: List<String>
    ): QueryToolsConditionsGroups {
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
    ): QueryToolsConditionsGroups {
        return whereCondition(_LOGICAL_AND, sideLeft, _OPERATION_IN, block);
    }



    override fun whereLike(
        sideLeft: String?,
        search: String? ,
        conditionLogical: String
    ): QueryToolsConditionsGroups {
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
    ): QueryToolsConditionsGroups {
        return whereCondition(conditionLogical, sideLeft, "", " is null ");
    }

    override fun whereNull(
        conditionLogical: String,
        block: (QueryBuilder) -> QueryBuilder,
        conditionOperation: String
    ): QueryToolsConditionsGroups {
        val sideLeft = block(QueryBuilder());
        return whereCondition(conditionLogical, "(${sideLeft.toSql().toString()})" , "", " is null ");
    }




    override fun whereCondition(
        conditionLogical: String,
        sideLeft: String?,
        conditionOperation: String,
        sideRight: String?
    ): QueryToolsConditionsGroups {
        val condition = QueryToolsConditions(conditionLogical, sideLeft, conditionOperation, sideRight);
        conditions.add(condition);
        return this;
    }

    override fun whereCondition(
        conditionLogical: String,
        sideLeft: String?,
        conditionOperation: String,
        block: (QueryBuilder) -> QueryBuilder?
    ): QueryToolsConditionsGroups {
        val sideRight = block(QueryBuilder());
        val condition = QueryToolsConditions(conditionLogical, sideLeft, conditionOperation, "(${sideRight?.toSql().toString()})");
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