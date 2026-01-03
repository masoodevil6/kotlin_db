package gog.my_project.query.interfaces.query_builders.tools.with.item

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsWithItemParams : IQueryTools {

    var params: MutableList<Any?>

    fun getWithName(): String?;
    fun getWithBody(): String?;

}