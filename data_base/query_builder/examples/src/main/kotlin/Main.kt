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

}