package gog.my_project.data_base.query_builder.examples.managers

import gog.my_project.data_base.query_builder.examples.v1.queries.A1ExampleV1
import gog.my_project.data_base.query_builder.examples.v1.queries.A2ExampleV1
import gog.my_project.data_base.query_builder.examples.v1.queries.A3ExampleV1
import gog.my_project.data_base.query_builder.examples.v1.queries.A4ExampleV1
import gog.my_project.data_base.query_builder.examples.v1.queries.A5ExampleV1
import gog.my_project.data_base.query_builder.examples.v1.queries.IAExampleV1
import gog.my_project.data_base.query_builder.renderer.manager.QueryManager


class ManagerExampleV1() : IManagerExample<IAExampleV1> {

    override var listExamples: ArrayList<IAExampleV1> = ArrayList()

    override fun readyListExamples() {
        listExamples.add(A1ExampleV1())   // V1-Ex1: select simple
        listExamples.add(A2ExampleV1())   // V1-Ex2: select with whereIn
        listExamples.add(A3ExampleV1())   // V1-Ex3: select with WhereLike
        listExamples.add(A4ExampleV1())   // V1-Ex3: select with WhereIsNull
        listExamples.add(A5ExampleV1())   // V1-Ex3: Create error
    }

    override fun renderExamples(queryManager: QueryManager) {
        for(example in listExamples) {
            example.execute(queryManager)
        }
    }

}