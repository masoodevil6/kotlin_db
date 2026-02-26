package gog.my_project.data_base.query.renderer.dialects

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass
import gog.my_project.data_base.query.dialect.manager.BaseSqlDialect
import gog.my_project.data_base.query.renderer.nodes.delete_nodes.query.MySqlQueryRenderDeleteCapability
import gog.my_project.data_base.query.renderer.nodes.insert_nodes.column_insert.MySqlQueryColumnInsertCapability
import gog.my_project.data_base.query.renderer.nodes.insert_nodes.query.MySqlQueryRenderInsertCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column.MySqlQueryColumnCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.column_base.MySqlQueryColumnBaseCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_group.MySqlQueryConditionGroupCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_item.MySqlQueryConditionCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_item_collection.MySqlQueryConditionCollectionCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.joins.MySqlQueryJoinsCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.joins_item.MySqlQueryJoinsItemCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_group.MySqlQueryOptionGroupCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_limit.MySqlQueryOptionLimitCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_offset.MySqlOptionOffsetCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.option_order.MySqlOptionOrderCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.query.MySqlQueryCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.select.MySqlQuerySelectCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.table.MySqlQueryTableCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.where.MySqlQueryWhereCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.withs.MySqlQueryWithsCapability
import gog.my_project.data_base.query.renderer.nodes.select_nodes.withs_item.MySqlQueryWithsItemCapability
import gog.my_project.data_base.query.renderer.nodes.update_nodes.column_update.MySqlQueryColumnUpdateCapability
import gog.my_project.data_base.query.renderer.nodes.update_nodes.query.MySqlQueryRenderUpdateCapability

class MySqlDialect(
) : BaseSqlDialect() {

    override val _prefixWiths:  String = "with";
    override val _prefixSelect: String = "select";
    override val _prefixTable:  String = "from"
    override val _prefixWhere:  String = "where";
    override val _prefixGroup:  String = "group by";
    override val _prefixLimit:  String = "limit";
    override val _prefixOrder:  String = "order by";
    override val _prefixOffset: String = "offset";
    override val _prefixInsert: String = "insert into"
    override val _prefixUpdate: String = "update"
    override val _prefixDelete: String = "delete";

    override fun registerRenders() {

        /// select
        _registry.register(IQueryColumnsAst::class                , MySqlQueryColumnCapability());
        _registry.register(IQueryColumnsBaseAst::class            , MySqlQueryColumnBaseCapability());
        _registry.register(IQueryConditionsGroupsAst::class       , MySqlQueryConditionGroupCapability());
        _registry.register(IQueryConditionsAst::class             , MySqlQueryConditionCapability());
        _registry.register(IQueryConditionsCollectionAst::class   , MySqlQueryConditionCollectionCapability());
        _registry.register(IQueryJoinsAst::class                  , MySqlQueryJoinsCapability());
        _registry.register(IQueryJoinsItemAst::class              , MySqlQueryJoinsItemCapability());
        _registry.register(IQueryOptionGroupAst::class            , MySqlQueryOptionGroupCapability());
        _registry.register(IQueryOptionLimitAst::class            , MySqlQueryOptionLimitCapability());
        _registry.register(IQueryOptionOrderAst::class            , MySqlOptionOrderCapability());
        _registry.register(IQueryOptionOffsetAst::class           , MySqlOptionOffsetCapability());
        _registry.register(IQueryRenderSelectAst::class           , MySqlQueryCapability());
        _registry.register(IQuerySelectAst::class                 , MySqlQuerySelectCapability());
        _registry.register(IQueryTableAst::class                  , MySqlQueryTableCapability());
        _registry.register(IQueryWhereAst::class                  , MySqlQueryWhereCapability());
        _registry.register(IQueryWithsAst::class                  , MySqlQueryWithsCapability());
        _registry.register(IQueryWithsItemAst::class              , MySqlQueryWithsItemCapability());

        /// insert
        _registry.register(IQueryRenderInsertAst::class           , MySqlQueryRenderInsertCapability());
        _registry.register(IQueryColumnInsertAst::class           , MySqlQueryColumnInsertCapability());

        /// update
        _registry.register(IQueryRenderUpdateAst::class           , MySqlQueryRenderUpdateCapability());
        _registry.register(IQueryColumnUpdateAst::class           , MySqlQueryColumnUpdateCapability());

        /// delete
        _registry.register(IQueryRenderDeleteAst::class           , MySqlQueryRenderDeleteCapability());

    }




}