#[derive(Debug, Clone, PartialEq)]
pub enum QueryStatement {
    Select {
        targets: Vec<Target>,
        from: Option<Expression>,
        where_clause: Option<Expression>,
        group_by: Option<Vec<Expression>>,
        order_by: Option<Vec<OrderBy>>,
        limit: Option<usize>,
    },
    Balances {
        from: Option<Expression>,
        where_clause: Option<Expression>,
    },
    Print {
        from: Option<Expression>,
        where_clause: Option<Expression>,
    },
}

#[derive(Debug, Clone, PartialEq)]
pub struct OrderBy {
    pub expr: Expression,
    pub desc: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Target {
    Star,
    Expression(Expression, Option<String>),
}

#[derive(Debug, Clone, PartialEq)]
pub enum Expression {
    Identifier(String),
    StringLiteral(String),
    Number(String),
    Date(String),
    Function(String, Vec<Expression>),
    BinaryOp(Box<Expression>, String, Box<Expression>),
}
