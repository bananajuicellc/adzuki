use crate::lexer::{CoreToken, SpannedToken};
use nom::{
    error::{Error, ErrorKind, ParseError},
    combinator::opt,
    IResult,
};

#[derive(Clone, Debug, PartialEq)]
pub struct TokenSlice<'a>(pub &'a [SpannedToken<CoreToken>]);

impl<'a> nom::Slice<std::ops::RangeFrom<usize>> for TokenSlice<'a> {
    fn slice(&self, range: std::ops::RangeFrom<usize>) -> Self {
        TokenSlice(&self.0[range])
    }
}

impl<'a> nom::Slice<std::ops::RangeTo<usize>> for TokenSlice<'a> {
    fn slice(&self, range: std::ops::RangeTo<usize>) -> Self {
        TokenSlice(&self.0[range])
    }
}

impl<'a> nom::Slice<std::ops::Range<usize>> for TokenSlice<'a> {
    fn slice(&self, range: std::ops::Range<usize>) -> Self {
        TokenSlice(&self.0[range])
    }
}

impl<'a> nom::Slice<std::ops::RangeFull> for TokenSlice<'a> {
    fn slice(&self, _: std::ops::RangeFull) -> Self {
        TokenSlice(self.0)
    }
}

impl<'a> nom::InputLength for TokenSlice<'a> {
    #[inline]
    fn input_len(&self) -> usize {
        self.0.len()
    }
}

impl<'a> nom::InputTake for TokenSlice<'a> {
    #[inline]
    fn take(&self, count: usize) -> Self {
        TokenSlice(&self.0[0..count])
    }

    #[inline]
    fn take_split(&self, count: usize) -> (Self, Self) {
        let (prefix, suffix) = self.0.split_at(count);
        (TokenSlice(suffix), TokenSlice(prefix))
    }
}

impl<'a> nom::InputLength for &TokenSlice<'a> {
    #[inline]
    fn input_len(&self) -> usize {
        self.0.len()
    }
}

#[derive(Debug, Clone, PartialEq)]
pub enum MdNode {
    Heading {
        level: u8,
        content: String,
        span: std::ops::Range<usize>,
    },
    Paragraph {
        content: String,
        span: std::ops::Range<usize>,
    },
    CodeBlock {
        language: Option<String>,
        tokens: Vec<(CoreToken, std::ops::Range<usize>)>,
        span: std::ops::Range<usize>,
    },
}

fn match_token<'a, E: ParseError<TokenSlice<'a>>>(
    expected: CoreToken,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, SpannedToken<CoreToken>, E> {
    move |i: TokenSlice<'a>| {
        if i.0.is_empty() {
            Err(nom::Err::Error(E::from_error_kind(i, ErrorKind::Eof)))
        } else if i.0[0].0 == expected {
            Ok((TokenSlice(&i.0[1..]), i.0[0].clone()))
        } else {
            Err(nom::Err::Error(E::from_error_kind(i, ErrorKind::Tag)))
        }
    }
}

fn match_tokens<'a, E: ParseError<TokenSlice<'a>>>(
    expected: &[CoreToken],
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, &'a [SpannedToken<CoreToken>], E> {
    let expected = expected.to_vec();
    move |i: TokenSlice<'a>| {
        if i.0.len() < expected.len() {
            return Err(nom::Err::Error(E::from_error_kind(i, ErrorKind::Eof)));
        }
        for (idx, exp) in expected.iter().enumerate() {
            if i.0[idx].0 != *exp {
                return Err(nom::Err::Error(E::from_error_kind(i, ErrorKind::Tag)));
            }
        }
        Ok((TokenSlice(&i.0[expected.len()..]), &i.0[..expected.len()]))
    }
}

fn take_until_newline<'a, E: ParseError<TokenSlice<'a>>>(
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, &'a [SpannedToken<CoreToken>], E> {
    move |i: TokenSlice<'a>| {
        for (idx, token) in i.0.iter().enumerate() {
            if token.0 == CoreToken::Newline {
                return Ok((TokenSlice(&i.0[idx..]), &i.0[..idx]));
            }
        }
        Ok((TokenSlice(&[]), i.0))
    }
}

fn reconstruct_string(tokens: &[SpannedToken<CoreToken>], source: &str) -> String {
    let mut s = String::new();
    for (_tok, span) in tokens {
        if span.start == 0 && span.end == 0 {
            if *_tok == CoreToken::PunctOrOther {
                s.push(';');
            } else if *_tok == CoreToken::Whitespace {
                s.push(' ');
            }
        } else {
            s.push_str(&source[span.clone()]);
        }
    }
    s
}

pub fn parse_heading<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, MdNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let start_idx = i.0.first().map(|(_, span)| span.start).unwrap_or(0);
        let mut level = 0;
        let mut cur_i = i.clone();

        while !cur_i.0.is_empty() && cur_i.0[0].0 == CoreToken::Hash {
            level += 1;
            cur_i = TokenSlice(&cur_i.0[1..]);
        }

        if level < 1 || level > 6 {
            return Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Tag)));
        }

        let (cur_i, _) = opt(match_token(CoreToken::Whitespace))(cur_i)?;
        let (cur_i, content_tokens) = take_until_newline()(cur_i)?;
        let (cur_i, _) = opt(match_token(CoreToken::Newline))(cur_i)?;

        let end_idx = if cur_i.0.is_empty() {
            source.len()
        } else {
            cur_i.0[0].1.start
        };

        Ok((
            cur_i,
            MdNode::Heading {
                level: level as u8,
                content: reconstruct_string(content_tokens, source).trim().to_string(),
                span: start_idx..end_idx,
            },
        ))
    }
}

pub fn parse_codeblock<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, MdNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let start_idx = i.0.first().map(|(_, span)| span.start).unwrap_or(0);
        let (mut i, _) = match_tokens(&[CoreToken::Backtick, CoreToken::Backtick, CoreToken::Backtick])(i)?;

        let mut lang = String::new();
        if !i.0.is_empty() && i.0[0].0 == CoreToken::Ident {
            lang = source[i.0[0].1.clone()].trim().to_string();
            i = TokenSlice(&i.0[1..]);
        }

        let (mut i, _) = take_until_newline()(i)?;
        let (mut i, _) = opt(match_token(CoreToken::Newline))(i)?;

        let mut inner_tokens = vec![];
        loop {
            if i.0.is_empty() {
                break;
            }
            if i.0.len() >= 3 && i.0[0].0 == CoreToken::Backtick && i.0[1].0 == CoreToken::Backtick && i.0[2].0 == CoreToken::Backtick {
                i = TokenSlice(&i.0[3..]);
                break;
            }
            inner_tokens.push(i.0[0].clone());
            i = TokenSlice(&i.0[1..]);
        }

        let end_idx = if i.0.is_empty() {
            source.len()
        } else {
            i.0[0].1.start
        };

        Ok((
            i,
            MdNode::CodeBlock {
                language: if lang.is_empty() { None } else { Some(lang) },
                tokens: inner_tokens.into_iter().map(|(t, r)| (t, r)).collect(),
                span: start_idx..end_idx,
            },
        ))
    }
}

pub fn parse_paragraph<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, MdNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let start_idx = i.0.first().map(|(_, span)| span.start).unwrap_or(0);
        let mut i = i;
        let mut content_tokens = vec![];

        if i.0.is_empty() {
            return Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Eof)));
        }
        if i.0[0].0 == CoreToken::Newline {
            return Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Tag)));
        }
        if i.0[0].0 == CoreToken::Hash || i.0[0].0 == CoreToken::Backtick {
            return Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Tag)));
        }

        loop {
            if i.0.is_empty() {
                break;
            }
            if i.0[0].0 == CoreToken::Newline {
                if i.0.len() > 1 && i.0[1].0 == CoreToken::Newline {
                    break; // double newline ends paragraph
                }
            }

            if i.0[0].0 == CoreToken::Newline && i.0.len() > 1 {
                let next_tok = &i.0[1];
                if next_tok.0 == CoreToken::Hash || next_tok.0 == CoreToken::Backtick {
                    break;
                }
            }

            content_tokens.push(i.0[0].clone());
            i = TokenSlice(&i.0[1..]);
        }

        while !i.0.is_empty() && i.0[0].0 == CoreToken::Newline {
            i = TokenSlice(&i.0[1..]);
        }

        let end_idx = if i.0.is_empty() {
            source.len()
        } else {
            i.0[0].1.start
        };

        Ok((
            i,
            MdNode::Paragraph {
                content: reconstruct_string(&content_tokens, source).trim().to_string(),
                span: start_idx..end_idx,
            },
        ))
    }
}

pub fn parse_markdown<'a>(
    source: &'a str,
    tokens: &'a [SpannedToken<CoreToken>],
) -> Vec<MdNode> {
    let mut nodes = vec![];
    let mut i = TokenSlice(tokens);

    while !i.0.is_empty() {
        if i.0[0].0 == CoreToken::Newline || i.0[0].0 == CoreToken::Whitespace {
            i = TokenSlice(&i.0[1..]);
            continue;
        }

        if let Ok((next_i, node)) = parse_heading(source)(i.clone()) {
            nodes.push(node);
            i = next_i;
            continue;
        }
        if let Ok((next_i, node)) = parse_codeblock(source)(i.clone()) {
            nodes.push(node);
            i = next_i;
            continue;
        }
        if let Ok((next_i, node)) = parse_paragraph(source)(i.clone()) {
            nodes.push(node);
            i = next_i;
            continue;
        }

        // Fallback
        i = TokenSlice(&i.0[1..]);
    }

    nodes
}
