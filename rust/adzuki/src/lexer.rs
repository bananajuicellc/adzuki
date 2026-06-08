use std::ops::Range;
use logos::Logos;

#[derive(Debug, Clone, PartialEq, Default)]
pub enum LexerError {
    #[default]
    Invalid,
}

#[allow(clippy::upper_case_acronyms)]
#[derive(Logos, Debug, PartialEq, Clone)]
#[logos(error = LexerError)]
pub enum CoreToken {
    #[regex(r"[ \t]+")]
    Whitespace,

    #[token("\n")]
    Newline,

    #[regex(r"[a-zA-Z_][a-zA-Z0-9_]*")]
    Ident,

    #[regex(r"[^ \t\n`#a-zA-Z_]+")]
    PunctOrOther,

    #[token("`")]
    Backtick,

    #[token("#")]
    Hash,
}

#[derive(Logos, Debug, PartialEq, Clone)]
#[logos(error = LexerError)]
pub enum BeancountToken {
    #[regex(r"[ \t]+")]
    Whitespace,

    #[token("\n")]
    Newline,

    #[regex(r"[0-9]{4}-[0-9]{2}-[0-9]{2}")]
    Date,

    #[regex(r"(Assets|Liabilities|Equity|Income|Expenses)(:[A-Z0-9][a-zA-Z0-9\-]*)+")]
    Account,

    #[regex(r"[A-Z][A-Z0-9'\.\_\-]{0,23}", priority = 3)]
    Currency,

    #[regex(r"-?([0-9|,]+(\.[0-9]+)?|\.[0-9]+)", priority = 3)]
    Number,

    #[regex(r#""([^"\\]|\\.)*""#)]
    StringLiteral,

    #[token("option")]
    OptionDirective,

    #[token("open")]
    OpenDirective,

    #[token("close")]
    CloseDirective,

    #[token("*")]
    #[token("!")]
    #[token("txn")]
    #[token("P")]
    #[token("#")]
    TxnFlag,

    #[token(",")]
    Comma,

    #[regex(r";[^\n]*", allow_greedy = true)]
    Comment,

    #[regex(r"[a-zA-Z_][a-zA-Z0-9_]*", priority = 2)]
    Word,

    #[regex(r"[^ \t\n]+", priority = 0)]
    Other,
}

pub type SpannedToken<T> = (T, Range<usize>);

pub fn lex_core(source: &str) -> Vec<SpannedToken<CoreToken>> {
    let mut lexer = CoreToken::lexer(source);
    let mut tokens = vec![];
    while let Some(res) = lexer.next() {
        if let Ok(token) = res {
            tokens.push((token, lexer.span()));
        } else {
            tokens.push((CoreToken::PunctOrOther, lexer.span()));
        }
    }
    tokens
}

pub fn lex_beancount(source: &str) -> Vec<SpannedToken<BeancountToken>> {
    let mut lexer = BeancountToken::lexer(source);
    let mut tokens = vec![];
    while let Some(res) = lexer.next() {
        if let Ok(token) = res {
            tokens.push((token, lexer.span()));
        } else {
            tokens.push((BeancountToken::Other, lexer.span()));
        }
    }
    tokens
}
