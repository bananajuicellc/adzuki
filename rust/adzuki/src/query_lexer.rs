use std::ops::Range;
use logos::Logos;

#[derive(Debug, Clone, PartialEq, Default)]
pub enum LexerError {
    #[default]
    Invalid,
}

#[derive(Logos, Debug, PartialEq, Clone)]
#[logos(error = LexerError)]
pub enum QueryToken {
    #[regex(r"[ \t\n]+")]
    Whitespace,

    #[token("SELECT", ignore(case))]
    Select,

    #[token("FROM", ignore(case))]
    From,

    #[token("WHERE", ignore(case))]
    Where,

    #[token("GROUP", ignore(case))]
    Group,

    #[token("ORDER", ignore(case))]
    Order,

    #[token("BY", ignore(case))]
    By,

    #[token("LIMIT", ignore(case))]
    Limit,

    #[token("DESC", ignore(case))]
    Desc,

    #[token("ASC", ignore(case))]
    Asc,

    #[token("BALANCES", ignore(case))]
    Balances,

    #[token("PRINT", ignore(case))]
    Print,

    #[token("AS", ignore(case))]
    As,

    #[token("AND", ignore(case))]
    And,

    #[token("OR", ignore(case))]
    Or,

    #[token("*")]
    Star,

    #[token(",")]
    Comma,

    #[token("(")]
    LParen,

    #[token(")")]
    RParen,

    #[token("=")]
    Eq,

    #[token("!=")]
    Neq,

    #[token("<")]
    Lt,

    #[token("<=")]
    Lte,

    #[token(">")]
    Gt,

    #[token(">=")]
    Gte,

    #[token("~")]
    Tilde,

    #[regex(r"[a-zA-Z_][a-zA-Z0-9_]*")]
    Ident,

    #[regex(r#""([^"\\]|\\.)*""#)]
    StringLiteral,

    #[regex(r"[0-9]{4}-[0-9]{2}-[0-9]{2}")]
    Date,

    #[regex(r"-?[0-9]+(\.[0-9]+)?")]
    Number,
}

pub type SpannedToken<T> = (T, Range<usize>);

pub fn lex_query(source: &str) -> Vec<SpannedToken<QueryToken>> {
    let mut lexer = QueryToken::lexer(source);
    let mut tokens = vec![];
    while let Some(res) = lexer.next() {
        if let Ok(token) = res {
            tokens.push((token, lexer.span()));
        }
    }
    tokens
}
