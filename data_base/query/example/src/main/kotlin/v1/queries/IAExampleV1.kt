package  gog.my_project.data_base.query.example.v1.queries

import gog.my_project.data_base.query.definition.interfaces.IQueryDefinition
import gog.my_project.data_base.query.renderer.manager.QueryManager


interface IAExampleV1 {

    fun query(): IQueryDefinition;

    fun execute(queryManager : QueryManager);

}