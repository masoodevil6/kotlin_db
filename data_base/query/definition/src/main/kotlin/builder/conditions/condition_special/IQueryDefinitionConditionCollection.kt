package gog.my_project.data_base.query.definition.interfaces.conditions.condition_special

interface IQueryDefinitionConditionCollection {

    fun <T> addParam(paramValue: T) : IQueryDefinitionConditionCollection;

}