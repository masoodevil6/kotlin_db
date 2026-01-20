package gog.my_project.data_base.query_builder.query.tools.enums

enum class SqlConditionOperation(val value: String) {
    Equals("=") ,
    NotEqual("<>")  ,
    GreaterThan(">")  ,
    GreaterThanOrEqual(">=")  ,
    LessThan("<")  ,
    LessThanOrEqual("<=")  ,
    Like("like")  ,
    NotLike("not like")  ,
    In("in")  ,
    NotIn("not in")  ,
    Between("between")  ,
    NotBetween("not between") ,
    IsNull("is null")  ,
    IsNotNull("is not null")  ,
    Contains("contains")  ,
}