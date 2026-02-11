package gog.my_project.data_base.query.renderer.dialects

import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.ast.interfaces.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.ast.interfaces.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.dialect.manager.BaseSqlDialect
import gog.my_project.data_base.query.renderer.nodes.column.MySqlQueryColumnCapability
import gog.my_project.data_base.query.renderer.nodes.column_base.MySqlQueryColumnBaseCapability
import gog.my_project.data_base.query.renderer.nodes.condition_group.MySqlQueryConditionGroupCapability
import gog.my_project.data_base.query.renderer.nodes.condition_item.MySqlQueryConditionCapability
import gog.my_project.data_base.query.renderer.nodes.condition_item_collection.MySqlQueryConditionCollectionCapability
import gog.my_project.data_base.query.renderer.nodes.joins.MySqlQueryJoinsCapability
import gog.my_project.data_base.query.renderer.nodes.joins_item.MySqlQueryJoinsItemCapability
import gog.my_project.data_base.query.renderer.nodes.option_group.MySqlQueryOptionGroupCapability
import gog.my_project.data_base.query.renderer.nodes.option_limit.MySqlQueryOptionLimitCapability
import gog.my_project.data_base.query.renderer.nodes.option_offset.MySqlOptionOffsetCapability
import gog.my_project.data_base.query.renderer.nodes.option_order.MySqlOptionOrderCapability
import gog.my_project.data_base.query.renderer.nodes.query.MySqlQueryCapability
import gog.my_project.data_base.query.renderer.nodes.select.MySqlQuerySelectCapability
import gog.my_project.data_base.query.renderer.nodes.table.MySqlQueryTableCapability
import gog.my_project.data_base.query.renderer.nodes.where.MySqlQueryWhereCapability
import gog.my_project.data_base.query.renderer.nodes.withs.MySqlQueryWithsCapability
import gog.my_project.data_base.query.renderer.nodes.withs_item.MySqlQueryWithsItemCapability

class MySqlDialect(
) : BaseSqlDialect() {

    override val _prefixWiths:  String = "withs";
    override val _prefixSelect: String = "select";
    override val _prefixTable:  String = "from"
    override val _prefixWhere:  String = "where";
    override val _prefixGroup:  String = "group by";
    override val _prefixLimit:  String = "limit";
    override val _prefixOrder:  String = "order by";
    override val _prefixOffset: String = "offset";

    override fun registerRenders() {

        _registry.register(IQueryColumnsAst::class                , MySqlQueryColumnCapability()               );
        _registry.register(IQueryColumnsBaseAst::class            , MySqlQueryColumnBaseCapability()           );
        _registry.register(IQueryConditionsGroupsAst::class       , MySqlQueryConditionGroupCapability()       );
        _registry.register(IQueryConditionsAst::class             , MySqlQueryConditionCapability()            );
        _registry.register(IQueryConditionsCollectionAst::class   , MySqlQueryConditionCollectionCapability()  );
        _registry.register(IQueryJoinsAst::class                  , MySqlQueryJoinsCapability()                );
        _registry.register(IQueryJoinsItemAst::class              , MySqlQueryJoinsItemCapability()            );
        _registry.register(IQueryOptionGroupAst::class            , MySqlQueryOptionGroupCapability()          );
        _registry.register(IQueryOptionLimitAst::class            , MySqlQueryOptionLimitCapability()          );
        _registry.register(IQueryOptionOrderAst::class            , MySqlOptionOrderCapability()               );
        _registry.register(IQueryOptionOffsetAst::class           , MySqlOptionOffsetCapability()              );
        _registry.register(IQueryRenderSelectAst::class           , MySqlQueryCapability()                     );
        _registry.register(IQuerySelectAst::class                 , MySqlQuerySelectCapability ()              );
        _registry.register(IQueryTableAst::class                  , MySqlQueryTableCapability()                );
        _registry.register(IQueryWhereAst::class                  , MySqlQueryWhereCapability()                );
        _registry.register(IQueryWithsAst::class                  , MySqlQueryWithsCapability()                );
        _registry.register(IQueryWithsItemAst::class              , MySqlQueryWithsItemCapability()            );

    }

}