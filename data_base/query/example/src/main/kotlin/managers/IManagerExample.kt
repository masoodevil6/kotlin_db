package gog.my_project.data_base.query.example.managers

import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

interface IManagerExample<T> {

    var listExamples: ArrayList<T>

    fun readyListExamples();

    fun renderExamples(queryManager: IQueryBuilderExecutor);
}