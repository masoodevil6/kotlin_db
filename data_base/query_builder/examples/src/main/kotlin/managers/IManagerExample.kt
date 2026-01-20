package gog.my_project.data_base.query_builder.examples.managers

import gog.my_project.data_base.query_builder.renderer.manager.QueryManager


interface IManagerExample<T> {

    var listExamples: ArrayList<T>

    fun readyListExamples();

    fun renderExamples(queryManager: QueryManager);
}