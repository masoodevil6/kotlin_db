package gog.my_project.data_base.query.example.managers

import gog.my_project.data_base.query.example.v1.queries.IExampleV1
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

interface IManagerExample {

    var listExamples: ArrayList<IExampleV1>

    fun readyListExamples();

    fun renderExamples(queryManager: IQueryBuilderExecutor);
}