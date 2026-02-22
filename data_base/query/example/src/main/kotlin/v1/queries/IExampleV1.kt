package gog.my_project.data_base.query.example.v1.queries

import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

interface IExampleV1<T> {

    fun query(): T;

    fun execute(queryManager : IQueryBuilderExecutor);

}