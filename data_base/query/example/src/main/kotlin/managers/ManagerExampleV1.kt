package gog.my_project.data_base.query.example.managers

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.example.v1.queries.select.A1ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A2ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A3ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A4ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A5ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A6ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A7ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor


class ManagerExampleV1() : IManagerExample {

    override var listExamples: ArrayList<IExampleV1> = ArrayList()

    override fun readyListExamples() {

        /// selects Queries
        listExamples.add(A1ExampleSelectV1())   // V1-Ex1: select simple
        listExamples.add(A2ExampleSelectV1())   // V1-Ex2: select with whereIn
        listExamples.add(A3ExampleSelectV1())   // V1-Ex3: select with WhereLike
        listExamples.add(A4ExampleSelectV1())   // V1-Ex4: select with WhereIsNull
        listExamples.add(A5ExampleSelectV1())   // V1-Ex5: Create error
        listExamples.add(A6ExampleSelectV1())   // V1-Ex6: Use Cte with
        listExamples.add(A7ExampleSelectV1())   // V1-Ex7: Query table attribute

        /// insert Queries

        /// update Queries

        /// delete Queries

    }

    override fun renderExamples(queryManager: IQueryBuilderExecutor) {
        for(example in listExamples) {
            example.execute(queryManager)
        }
    }

}