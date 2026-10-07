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
) : IManagerExample<IQueryRenderSelectApi, IQueryRenderInsertApi, IQueryRenderUpdateApi, IQueryRenderDeleteApi> {
    override var listExamplesSelect: ArrayList<IExampleV1<IQueryRenderSelectApi>> = arrayListOf()
    override var listExamplesInsert: ArrayList<IExampleV1<IQueryRenderInsertApi>> = arrayListOf()
    override var listExamplesUpdate: ArrayList<IExampleV1<IQueryRenderUpdateApi>> = arrayListOf()
    override var listExamplesDelete: ArrayList<IExampleV1<IQueryRenderDeleteApi>> = arrayListOf()

    override fun readyListExamples() {
        listExamplesSelect.addAll(
            listOf(
                A1ExampleSelectV1(), A2ExampleSelectV1(), A3ExampleSelectV1(),
                A4ExampleSelectV1(), A5ExampleSelectV1(), A6ExampleSelectV1(), A7ExampleSelectV1(),
            ),
        )
        listExamplesInsert.add(A1ExampleInsertV1())
        listExamplesUpdate.add(A1ExampleUpdateV1())
        listExamplesDelete.add(A1ExampleDeleteV1())
    }
}
