package gog.my_project.query.interfaces.query_builders.tools

interface IQueryTools {

    fun toSql(): String?;
    fun replaceInBaseTemp(query: String): String;

}