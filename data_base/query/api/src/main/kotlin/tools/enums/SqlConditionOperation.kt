package gog.my_project.data_base.query.api.tools.enums

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
    Is("is")  ,
    IsNot("is not")  ,
    Contains("contains")  ,
}