package gog.my_project.data_base.query_builder.query.interfaces.join

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools


interface IQueryToolsJoinsConnect : IQueryTools {

    fun getListJoins(): List<IQueryToolsJoinsItem>

    fun addJoin(blockJoin: IQueryToolsJoinsItem.()-> IQueryToolsJoinsItem): IQueryToolsJoinsConnect;

}