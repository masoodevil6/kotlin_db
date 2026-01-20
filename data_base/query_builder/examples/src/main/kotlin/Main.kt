package gog.my_project.data_base.query_builder.examples

import gog.my_project.data_base.query_builder.examples.managers.ManagerExampleV1
import gog.my_project.data_base.query_builder.renderer.manager.QueryManager


//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {

    val queryManager = QueryManager();

    val managerV1 = ManagerExampleV1();
    managerV1.readyListExamples()
    managerV1.renderExamples(queryManager);


    /*val dbBuilder =  DatabaseBuilder()
        .config {
            domain("jdbc:mysql://127.0.0.1")
            port(3306)
            name("kotlin_db")
            username("root")
            password("")
        }
        .dialect(DialectQuery.MY_SQL)
        .execute()
        {
            dialect ->
            return query.readyExecuteSql(dialect)
        }
        {
                result->
            while (result!!.next()){
                val id =       result.getInt("id")
                val name =     result.getString("name")
                val family =   result.getString("family")
                val age =      result.getInt("age")
                val phone =    result.getString("phone")
                println("User: - $id $name $family $age $phone");
            }
        }*/


}