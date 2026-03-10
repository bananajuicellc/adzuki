use super::parser::{Diagnostic, Span};
use codespan_reporting::diagnostic::Label;
use logos::Logos;

#[derive(Debug, Clone, PartialEq, Default)]
pub enum LexerError {
    #[default]
    Invalid,
}

impl LexerError {
    pub fn into_diagnostic(self, span: Span) -> Diagnostic {
        match self {
            Self::Invalid => Diagnostic::error()
                .with_message("invalid token")
                .with_label(Label::primary((), span)),
        }
    }
}

#[allow(clippy::upper_case_acronyms)]
#[derive(Logos, Debug, PartialEq, Copy, Clone)]
#[logos(error = LexerError)]
pub enum Token {
    EOF,
    #[token("# ")]
    Heading1,
    #[token("## ")]
    Heading2,
    #[token("### ")]
    Heading3,
    #[token("#### ")]
    Heading4,
    #[token("##### ")]
    Heading5,
    #[token("###### ")]
    Heading6,
    #[regex(r"([^#\n` \t]|#[^ ]|`[^`])+")]
    Text,
    #[regex("```[a-zA-Z0-9]*\n([^`]|`[^`]|``[^`])*```")]
    CodeBlock,
    #[token("\n")]
    Newline,
    #[regex(r"[ \t]+")]
    Whitespace,
    #[token("<error>")]
    Error,
}

pub fn tokenize(source: &str, diags: &mut Vec<Diagnostic>) -> (Vec<Token>, Vec<Span>) {
    let lexer = Token::lexer(source);
    let mut tokens = vec![];
    let mut spans = vec![];

    for (token, span) in lexer.spanned() {
        match token {
            Ok(token) => {
                tokens.push(token);
            }
            Err(err) => {
                diags.push(err.into_diagnostic(span.clone()));
                tokens.push(Token::Error);
            }
        }
        spans.push(span);
    }
    tokens.push(Token::EOF);
    spans.push(source.len()..source.len());
    (tokens, spans)
}
