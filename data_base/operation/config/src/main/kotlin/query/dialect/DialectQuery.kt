package gog.my_project.data_base_operation.config.query.dialect

sealed class DialectQuery {
    object MY_SQL : DialectQuery()
    object MARIA_DB : DialectQuery()
    //object SQL_LITE : DialectQuery()
}
