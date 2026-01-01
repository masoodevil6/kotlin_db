package gog.my_project.query.interfaces.query_builders.tools.join

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools


interface IQueryToolsJoinsConnect : IQueryTools {

    fun getListJoins(): List<IQueryToolsJoinsItem>

    fun addJoin(blockJoin: IQueryToolsJoinsItem.()-> IQueryToolsJoinsItem): IQueryToolsJoinsConnect;

}