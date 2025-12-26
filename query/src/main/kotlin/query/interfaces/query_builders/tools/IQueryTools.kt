package gog.my_project.query.interfaces.query_builders.tools

interface IQueryTools {

    public fun getBaseTempSql(): String?;
    public fun toSql(): String?;
    public fun replaceInBaseTemp(query: String): String;

}