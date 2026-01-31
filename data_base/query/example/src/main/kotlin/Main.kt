package gog.my_project.data_base.query.example

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {

    val queryManager = QueryManager();

    val managerV1 = ManagerExampleV1();
    managerV1.readyListExamples()
    managerV1.renderExamples(queryManager);

}