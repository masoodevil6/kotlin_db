package gog.my_project.data_base.query.ast.metadata.select.conditions_item

import gog.my_project.data_base.query.ast.metadata.select.conditions.QueryConditionsAstMetadata

class QueryConditionsItemAstMetadata : QueryConditionsAstMetadata() {
    var conditionLogical: String? = null
    var conditionOperation: String? = null
}
