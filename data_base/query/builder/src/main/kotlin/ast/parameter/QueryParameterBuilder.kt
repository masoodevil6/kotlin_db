package gog.my_project.data_base.query.builder.ast.parameter

import gog.my_project.data_base.core.query.reader.SqlTypeResolver
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

/** Completes one externally supplied parameter AST node from the scalar DSL. */
internal class QueryParameterBuilder<T>(
    private val ast: IQueryParameterAst<T>,
    private val defaultName: () -> String,
) : IQueryParameterApi<T> {
    private var nameCalls = 0
    private var valueCalls = 0


    override fun paramName(name: String): IQueryParameterApi<T> {
        nameCalls++
        ast.metadata.name = name
        return this
    }

    override fun paramValue(value: T): IQueryParameterApi<T> {
        valueCalls++
        ast.metadata.value = value
        ast.metadata.sqlType = SqlTypeResolver.resolve(value)
        return this
    }

    fun validate() {
        check(valueCalls > 0) {
            "Parameter value must be provided"
        }
        check(valueCalls == 1) {
            "Parameter value can only be provided once"
        }
        check(nameCalls <= 1) {
            "Parameter name can only be provided once"
        }
        if (nameCalls == 0) {
            ast.metadata.name = defaultName()
        }
        require(ast.metadata.name?.matches(VALID_NAME) == true) {
            "Parameter name must match ${VALID_NAME.pattern}"
        }
    }

    private companion object {
        val VALID_NAME = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
    }
}
