package gog.my_project.data_base.query.example

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.query.example.managers.ManagerExampleV1
import gog.my_project.data_base.query.executer.manager.QueryBuilderExecutor

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {


    DefaultDatabaseConfig.config=
        DatabaseConfig(
            dbDomain = "jdbc:mysql://127.0.0.1",
            dbPort = 3306,
            dbName = "kotlin_db",
            dbUserName = "root",
            dbPassword = "",
            dbPoolSize = 10
        );
    DefaultDatabaseConfig.dialect = DialectQuery.MY_SQL;


    val queryManager = QueryBuilderExecutor();

    val managerV1 = ManagerExampleV1();
    managerV1.readyListExamples()
    managerV1.renderExamples(queryManager);

}