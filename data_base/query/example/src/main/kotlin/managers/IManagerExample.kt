package gog.my_project.data_base.query.example.managers

import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

interface IManagerExample<ApiSelect , ApiInsert> {
    val statusRunSelect: Boolean;
    val statusRunInsert: Boolean;
    val statusRunUpdate: Boolean;
    val statusRunDelete: Boolean;

    var listExamplesSelect: ArrayList<IExampleV1<ApiSelect>>
    var listExamplesInsert: ArrayList<IExampleV1<ApiInsert>>


    fun readyListExamples();


    fun renderExamples(queryManager: IQueryBuilderExecutor) {
        /// selects Queries
        if (this.statusRunSelect){
            for(exampleSelect in listExamplesSelect) {
                exampleSelect.execute(queryManager)
            }
        }

        /// insert Queries
        if (this.statusRunInsert){
            for(exampleInsert in listExamplesInsert) {
                exampleInsert.execute(queryManager)
            }
        }

        /// update Queries
        if (this.statusRunUpdate){

        }

        /// delete Queries
        if (this.statusRunDelete){

        }
    }
}