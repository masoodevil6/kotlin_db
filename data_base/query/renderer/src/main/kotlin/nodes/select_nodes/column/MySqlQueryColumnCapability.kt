package gog.my_project.data_base.query.renderer.nodes.select_nodes.column

import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column.QueryColumnData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.concat.QueryColumnMethodConcatData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.avg.QueryColumnMethodAvgData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.count.IQueryColumnMethodCountData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.max.QueryColumnMethodMaxData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.min.QueryColumnMethodMinData
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.sum.QueryColumnMethodSumData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column.IQueryColumnCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer

class MySqlQueryColumnCapability :
    IQueryColumnCapability, IParameterOccurrenceAwareRenderer {

    override fun render(
        ast:       IQueryColumnsAst,
        ctx:       IRenderContext,
        dataClass: QueryColumnData?
    ): String? {

        val columnAlias: String? = if (ast.metadata.IsColumnAliasExplicit) {
            ast.metadata.ColumnAlias
        } else {
            ast.metadata.ColumnAlias ?: ast.Column?.metadata?.resolveColumnAlias()
        }
        val columnMethod :   String?               = ast.metadata.ColumnMethod;
        val columnBase :     IQueryColumnsBaseAst? = ast.Column;

        var strOut : String? = null;
        if (columnMethod != null) {
            var methodName = "";
            var methodArg : String? = null;
            when(columnMethod){
                SqlMethodColumn.Count.value ->{
                    methodName = ctx.dialect._prefixMethodCount;
                    methodArg = ctx.registry.render(
                        ast.ColumnMethodCount,
                        ctx.dialect,
                        IQueryColumnMethodCountData()
                    )
                }
                SqlMethodColumn.Concat.value ->{
                    methodName = ctx.dialect._prefixMethodConcat;
                    methodArg = ctx.registry.render(
                        ast.ColumnMethodConcat,
                        ctx.dialect,
                        QueryColumnMethodConcatData()
                    )
                }
                SqlMethodColumn.Min.value ->{
                    methodName = ctx.dialect._prefixMethodMin;
                    methodArg = ctx.registry.render(
                        ast.ColumnMethodMin,
                        ctx.dialect,
                        QueryColumnMethodMinData()
                    )
                }
                SqlMethodColumn.Max.value ->{
                    methodName = ctx.dialect._prefixMethodMax;
                    methodArg = ctx.registry.render(
                        ast.ColumnMethodMax,
                        ctx.dialect,
                        QueryColumnMethodMaxData()
                    )
                }
                SqlMethodColumn.Sum.value -> {
                    methodName = ctx.dialect._prefixMethodSum
                    methodArg = ctx.registry.render(
                        ast.ColumnMethodSum,
                        ctx.dialect,
                        QueryColumnMethodSumData()
                    )
                }
                SqlMethodColumn.Avg.value -> {
                    methodName = ctx.dialect._prefixMethodAvg
                    methodArg =  ctx.registry.render(
                        ast.ColumnMethodAvg,
                        ctx.dialect,
                        QueryColumnMethodAvgData()
                    )
                }
            }

            if (methodArg != null){
                strOut = "${methodName}${methodArg}";
            }
        }
        else{
            strOut =  ctx.registry.render(
                columnBase ,
                ctx.dialect ,
                QueryColumnBaseData()
            )
        }

        if (strOut != null){
            if (columnAlias != null) {
                strOut += " As ${columnAlias} ";
            }

            return strOut;
        }
        return null

    }


}
