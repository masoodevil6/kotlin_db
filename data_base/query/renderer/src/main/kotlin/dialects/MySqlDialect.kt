package gog.my_project.data_base.query.renderer.dialects

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.avg.IQueryColumnMethodAvgAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.count.IQueryColumnMethodCountAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.max.IQueryColumnMethodMaxAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.sum.IQueryColumnMethodSumAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.select_lock_row.IQuerySelectLockRowAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.data_class.parameter_data.ParameterRenderAttempt
import gog.my_project.data_base.query.dialect.manager.BaseSqlDialect
import gog.my_project.data_base.query.renderer.nodes.delete_nodes.query.MySqlQueryRenderDeleteCapability
import gog.my_project.data_base.query.renderer.nodes.delete_nodes.query.MySqlQueryRenderParameterCapability
import gog.my_project.data_base.query.renderer.nodes.insert_nodes.column_insert.MySqlQueryColumnInsertCapability
import gog.my_project.data_base.query.renderer.nodes.insert_nodes.query.MySqlQueryRenderInsertCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column.MySqlQueryColumnCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_base.MySqlQueryColumnBaseCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.concat.MySqlQueryColumnMethodConcatCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.avg.MySqlQueryColumnMethodAvgCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.count.MySqlQueryColumnMethodCountCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.max.MySqlQueryColumnMethodMaxCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.min.MySqlQueryColumnMethodMinCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_method.sum.MySqlQueryColumnMethodSumCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_over.MySqlQueryColumnOverCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_group.MySqlQueryConditionGroupCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_item.MySqlQueryConditionCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_item_collection.MySqlQueryConditionCollectionCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.joins.MySqlQueryJoinsCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.joins_item.MySqlQueryJoinsItemCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_group.MySqlQueryOptionGroupCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_limit.MySqlQueryOptionLimitCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_offset.MySqlOptionOffsetCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_order.MySqlOptionOrderCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_partition.MySqlQueryOptionPartitionCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.query.MySqlQueryCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.select.MySqlQuerySelectCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.select_lock_row.MySqlQuerySelectLockRowCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.table.MySqlQueryTableCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.where.MySqlQueryWhereCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.withs.MySqlQueryWithsCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.withs_item.MySqlQueryWithsItemCapability
import gog.my_project.data_base.query.renderer.nodes.update_nodes.column_update.MySqlQueryColumnUpdateCapability
import gog.my_project.data_base.query.renderer.nodes.update_nodes.query.MySqlQueryRenderUpdateCapability

open class MySqlDialect : BaseSqlDialect() {

    /** Parameter source classification is based on actual placeholder emission, not AST discovery. */
    internal fun renderParameterAware(ast: IQueryAst<*>): ParameterRenderAttempt {
        require(
            ast is IQueryRenderSelectAst ||
                ast is IQueryRenderInsertAst ||
                ast is IQueryRenderUpdateAst ||
                ast is IQueryRenderDeleteAst,
        ) {
            "Parameter-aware rendering requires a SELECT, INSERT, UPDATE, or DELETE AST"
        }
        return renderParameterAwarePasses(ast)
    }

    override val _prefixWiths:  String =       "with";
    override val _prefixSelect: String =       "select";
    override val _prefixSelectLock: String =   "for update";
    override val _prefixTable:  String =       "from"
    override val _prefixWhere:  String =       "where";

    override val _prefixGroup:  String =       "group by";
    override val _prefixLimit:  String =       "limit";
    override val _prefixOrder:  String =       "order by";
    override val _prefixOffset: String =       "offset";

    override val _prefixDistinct: String =     "distinct";
    override val _prefixOver: String =         "over";
    override val _prefixPartitionBy: String =  "partition by";

    override val _prefixInsert: String =       "insert into"
    override val _prefixUpdate: String =       "update"
    override val _prefixDelete: String =       "delete";

    override val _prefixMethodCount: String =  "count";
    override val _prefixMethodConcat: String = "concat";
    override val _prefixMethodMin: String =    "min";
    override val _prefixMethodMax: String =    "max";
    override val _prefixMethodSum: String =    "sum";
    override val _prefixMethodAvg: String =    "avg";

    override fun registerRenders() {

        /// select
        _registry.register(IQueryColumnsAst::class                         , MySqlQueryColumnCapability());
        _registry.register(IQueryColumnsBaseAst::class                     , MySqlQueryColumnBaseCapability());
        _registry.register(IQueryConditionsGroupsAst::class                , MySqlQueryConditionGroupCapability());
        _registry.register(IQueryConditionsAst::class                      , MySqlQueryConditionCapability());
        _registry.register(IQueryConditionsCollectionAst::class            , MySqlQueryConditionCollectionCapability());
        _registry.register(IQueryJoinsAst::class                           , MySqlQueryJoinsCapability());
        _registry.register(IQueryJoinsItemAst::class                       , MySqlQueryJoinsItemCapability());

        _registry.register(IQueryRenderSelectAst::class                    , MySqlQueryCapability());
        _registry.register(IQueryTableAst::class                           , MySqlQueryTableCapability());
        _registry.register(IQueryWhereAst::class                           , MySqlQueryWhereCapability());
        _registry.register(IQueryWithsAst::class                           , MySqlQueryWithsCapability());
        _registry.register(IQueryWithsItemAst::class                       , MySqlQueryWithsItemCapability());

        _registry.register(IQuerySelectAst::class                          , MySqlQuerySelectCapability());
        _registry.register(IQuerySelectLockRowAst::class                   , MySqlQuerySelectLockRowCapability());

        _registry.register(IQueryColumnMethodConcatAst::class              , MySqlQueryColumnMethodConcatCapability());
        _registry.register(IQueryColumnMethodCountAst::class               , MySqlQueryColumnMethodCountCapability());
        _registry.register(IQueryColumnMethodMinAst::class                 , MySqlQueryColumnMethodMinCapability());
        _registry.register(IQueryColumnMethodMaxAst::class                 , MySqlQueryColumnMethodMaxCapability());
        _registry.register(IQueryColumnMethodSumAst::class                 , MySqlQueryColumnMethodSumCapability());
        _registry.register(IQueryColumnMethodAvgAst::class                 , MySqlQueryColumnMethodAvgCapability());

        _registry.register(IQueryColumnOverAst::class                      , MySqlQueryColumnOverCapability());

        _registry.register(IQueryOptionGroupAst::class                     , MySqlQueryOptionGroupCapability());
        _registry.register(IQueryOptionLimitAst::class                     , MySqlQueryOptionLimitCapability());
        _registry.register(IQueryOptionOrderAst::class                     , MySqlOptionOrderCapability());
        _registry.register(IQueryOptionOffsetAst::class                    , MySqlOptionOffsetCapability());
        _registry.register(IQueryOptionPartitionAst::class                 , MySqlQueryOptionPartitionCapability());


        /// insert
        _registry.register(IQueryRenderInsertAst::class                    , MySqlQueryRenderInsertCapability());
        _registry.register(IQueryColumnInsertAst::class                    , MySqlQueryColumnInsertCapability());

        /// update
        _registry.register(IQueryRenderUpdateAst::class                    , MySqlQueryRenderUpdateCapability());
        _registry.register(IQueryColumnUpdateAst::class                    , MySqlQueryColumnUpdateCapability());

        /// delete
        _registry.register(IQueryRenderDeleteAst::class                    , MySqlQueryRenderDeleteCapability());

        /// parameter
        _registry.register(IQueryParameterAst::class                       , MySqlQueryRenderParameterCapability());

    }




}
