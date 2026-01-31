package gog.my_project.data_base.query.definition.interfaces.join

interface IQueryDefinitionJoinsConnect {

    fun addJoin(blockJoin: IQueryDefinitionJoinsItem.()-> IQueryDefinitionJoinsItem): IQueryDefinitionJoinsConnect;


}