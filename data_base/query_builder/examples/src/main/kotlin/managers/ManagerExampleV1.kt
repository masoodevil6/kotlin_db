package gog.my_project.data_base.query_builder.examples.managers

import gog.my_project.data_base.query_builder.examples.v1.queries.A1ExampleV1
import gog.my_project.data_base.query_builder.examples.v1.queries.IAExampleV1
import gog.my_project.data_base.query_builder.renderer.manager.QueryManager


class ManagerExampleV1() : IManagerExample<IAExampleV1> {

    override var listExamples: ArrayList<IAExampleV1> = ArrayList()

    override fun readyListExamples() {
        listExamples.add(A1ExampleV1())
    }

    override fun renderExamples(queryManager: QueryManager) {
        for(example in listExamples) {
            example.execute(queryManager)
        }
    }

}