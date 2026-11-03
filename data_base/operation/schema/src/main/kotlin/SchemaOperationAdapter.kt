package gog.my_project.data_base_schema

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi

/** Internal bridge to the existing Schema pipeline; it retains one API object without copying it. */
internal class ExistingSchemaOperationAdapter(
    internal val schemaApi: ISchemaApi<*>,
) : SchemaOperation

/** Wraps exactly one existing Schema operation. AST access remains internal. */
internal fun adaptSchemaOperation(schemaApi: ISchemaApi<*>?): SchemaOperation =
    ExistingSchemaOperationAdapter(
        requireNotNull(schemaApi) { "Schema operation must not be null" },
    )

/** Resolves this foundation handle back to the existing pipeline without reflection or casts. */
internal fun SchemaOperation.requireSchemaApi(): ISchemaApi<*> =
    (this as? ExistingSchemaOperationAdapter)?.schemaApi
        ?: throw IllegalArgumentException("Schema operation was not created by the Schema facade")
