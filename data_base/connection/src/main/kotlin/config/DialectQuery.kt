package gog.my_project.data_base.connection.config

sealed class DialectQuery {
    object MY_SQL : DialectQuery()
    //object SQL_LITE : DialectQuery()
}