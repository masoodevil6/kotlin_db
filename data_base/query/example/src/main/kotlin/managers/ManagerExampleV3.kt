package gog.my_project.data_base.query.example.managers

import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.example.v3.queries.delete.A1ExampleDeleteV3
import gog.my_project.data_base.query.example.v3.queries.insert.A1ExampleInsertV3
import gog.my_project.data_base.query.example.v3.queries.select.A1ExampleSelectV3
import gog.my_project.data_base.query.example.v3.queries.select.A2ExampleSelectV3
import gog.my_project.data_base.query.example.v3.queries.select.A3ExampleSelectV3
import gog.my_project.data_base.query.example.v3.queries.select.A4ExampleSelectV3
import gog.my_project.data_base.query.example.v3.queries.select.A5ExampleSelectV3
import gog.my_project.data_base.query.example.v3.queries.select.A6ExampleSelectV3
import gog.my_project.data_base.query.example.v3.queries.select.A7ExampleSelectV3
import gog.my_project.data_base.query.example.v3.queries.update.A1ExampleUpdateV3

class ManagerExampleV3(
    override val statusRunSelect: Boolean = true,
    override val statusRunInsert: Boolean = false,
    override val statusRunUpdate: Boolean = false,
    override val statusRunDelete: Boolean = false,
) : IManagerExample<IQueryRenderSelectApi, IQueryRenderInsertApi, IQueryRenderUpdateApi, IQueryRenderDeleteApi> {
    override var listExamplesSelect: ArrayList<IExampleV1<IQueryRenderSelectApi>> = arrayListOf()
    override var listExamplesInsert: ArrayList<IExampleV1<IQueryRenderInsertApi>> = arrayListOf()
    override var listExamplesUpdate: ArrayList<IExampleV1<IQueryRenderUpdateApi>> = arrayListOf()
    override var listExamplesDelete: ArrayList<IExampleV1<IQueryRenderDeleteApi>> = arrayListOf()

    override fun readyListExamples() {
        listExamplesInsert.add(A1ExampleInsertV3())
        listExamplesUpdate.add(A1ExampleUpdateV3())
        listExamplesDelete.add(A1ExampleDeleteV3())

        listExamplesSelect.add(A1ExampleSelectV3())
        listExamplesSelect.add(A2ExampleSelectV3())
        listExamplesSelect.add(A3ExampleSelectV3())
        listExamplesSelect.add(A4ExampleSelectV3())
        listExamplesSelect.add(A5ExampleSelectV3())
        listExamplesSelect.add(A6ExampleSelectV3())
        listExamplesSelect.add(A7ExampleSelectV3())
    }
}
