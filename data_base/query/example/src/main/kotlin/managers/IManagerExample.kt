package gog.my_project.data_base.query.example.managers

import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

interface IManagerExample<ApiSelect , ApiInsert , ApiUpdate, ApiDelete> {
    val statusRunSelect: Boolean;
    val statusRunInsert: Boolean;
    val statusRunUpdate: Boolean;
    val statusRunDelete: Boolean;

    var listExamplesSelect: ArrayList<IExampleV1<ApiSelect>>
    var listExamplesInsert: ArrayList<IExampleV1<ApiInsert>>
    var listExamplesUpdate: ArrayList<IExampleV1<ApiUpdate>>
    var listExamplesDelete: ArrayList<IExampleV1<ApiDelete>>


    fun readyListExamples();


    fun renderExamples(queryManager: IQueryBuilderExecutor) {
        fun <Api> executeExamples(examples: List<IExampleV1<Api>>) {
            for (example in examples) {
                try {
                    example.execute(queryManager)
                } catch (failure: Exception) {
                    val exampleName = example::class.qualifiedName ?: example::class.simpleName ?: "unknown"
                    println("Example $exampleName failed: ${failure.message}")
                }
            }
        }

        /// selects Queries
        if (this.statusRunSelect){
            executeExamples(listExamplesSelect)
        }

        /// insert Queries
        if (this.statusRunInsert){
            executeExamples(listExamplesInsert)
        }

        /// update Queries
        if (this.statusRunUpdate){
            executeExamples(listExamplesUpdate)
        }

        /// delete Queries
        if (this.statusRunDelete){
            executeExamples(listExamplesDelete)
        }
    }
}
