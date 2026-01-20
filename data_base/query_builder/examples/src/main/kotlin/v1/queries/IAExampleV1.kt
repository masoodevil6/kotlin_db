package  gog.my_project.data_base.query_builder.examples.v1.queries

import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.renderer.manager.QueryManager

interface IAExampleV1 {

    fun query(): IQueryBuilder;

    fun execute(queryManager : QueryManager);

}