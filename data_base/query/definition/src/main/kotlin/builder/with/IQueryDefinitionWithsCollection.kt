package gog.my_project.data_base.query.definition.interfaces.with

interface IQueryDefinitionWithsCollection {

    fun addWith(blockWith: IQueryDefinitionWithsItem.() -> IQueryDefinitionWithsItem) : IQueryDefinitionWithsCollection;

}