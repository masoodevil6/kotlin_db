package gog.my_project.app.config.config

//import data.base.sql_dialect.ITemplateSqlType
//import utils.params.ObjectSqlTypes

object DatabaseConfig{

    // val dbDomain    : String = "jdbc:mysql://localhost",
    val dbDomain    : String = "jdbc:mysql://127.0.0.1";
    val dbPort      : Int    = 3306;
    val dbName      : String = "kotlin_db";
    val dbUserName  : String = "root";
    val dbPassword  : String = "" ;
    val dbPoolSize  : Int    = 10 ;

    //val dbTypeName : ITemplateSqlType = ObjectSqlTypes._TYPE_MY_SQL ;

}