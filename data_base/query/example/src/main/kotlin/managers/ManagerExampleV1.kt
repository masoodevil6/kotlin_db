package gog.my_project.data_base.query.example.managers

import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.example.v1.queries.delete.A1ExampleDeleteV1
import gog.my_project.data_base.query.example.v1.queries.insert.A1ExampleInsertV1
import gog.my_project.data_base.query.example.v1.queries.select.A1ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A2ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A3ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A4ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A5ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A6ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.select.A7ExampleSelectV1
import gog.my_project.data_base.query.example.v1.queries.update.A1ExampleUpdateV1


class ManagerExampleV1(
    override val statusRunSelect: Boolean = true,
    override val statusRunInsert: Boolean = true,
    override val statusRunUpdate: Boolean = true,
    override val statusRunDelete: Boolean = true,
) : IManagerExample<IQueryRenderSelectApi , IQueryRenderInsertApi , IQueryRenderUpdateApi , IQueryRenderDeleteApi> {

    override var listExamplesSelect: ArrayList<IExampleV1<IQueryRenderSelectApi>> = ArrayList();
    override var listExamplesInsert: ArrayList<IExampleV1<IQueryRenderInsertApi>> = ArrayList();
    override var listExamplesUpdate: ArrayList<IExampleV1<IQueryRenderUpdateApi>> = ArrayList();
    override var listExamplesDelete: ArrayList<IExampleV1<IQueryRenderDeleteApi>> = ArrayList();


    override fun readyListExamples() {

        /// selects Queries
        listExamplesSelect.add(A1ExampleSelectV1())   // V1-Ex1: select simple
        listExamplesSelect.add(A2ExampleSelectV1())   // V1-Ex2: select with whereIn
        listExamplesSelect.add(A3ExampleSelectV1())   // V1-Ex3: select with WhereLike
        listExamplesSelect.add(A4ExampleSelectV1())   // V1-Ex4: select with WhereIsNull
        listExamplesSelect.add(A5ExampleSelectV1())   // V1-Ex5: Create error
        listExamplesSelect.add(A6ExampleSelectV1())   // V1-Ex6: Use Cte with
        listExamplesSelect.add(A7ExampleSelectV1())   // V1-Ex7: Query table attribute

        /// insert Queries
        listExamplesInsert.add(A1ExampleInsertV1())   // V1-Ex7: Query Insert sample

        /// update Queries
        listExamplesUpdate.add(A1ExampleUpdateV1())   // V1-Ex7: Query Update sample

        /// delete Queries
        listExamplesDelete.add(A1ExampleDeleteV1())   // V1-Ex7: Query Delete sample

    }



}