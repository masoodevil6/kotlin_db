package gog.my_project.data_base.core.query.reader

data class BuiltQuery(
    val query: String? ,
    val params: MutableList<SqlParameter<*>>
){

    fun getListParamNames() : List<String>{
        val paramsNames = mutableSetOf<String>()
        if (query != null) {
            val pattern = Regex(":([a-zA-Z_][a-zA-Z0-9_]*)");
            pattern.findAll(query).forEach { match->
                paramsNames.add(match.groupValues[1])
            };
        }
        return paramsNames.toList();
    }


    fun getReadyQuery() : String?{
        var resultExp: String? = null
        if (query != null) {
            val paramsNames = this.getListParamNames();

            resultExp = query;
            for (paramName in paramsNames) {
                resultExp = resultExp?.replace(":$paramName" , "?")
            }
        }
        return resultExp;
    }

}
