use crate::lexer::{CoreToken, SpannedToken};
use nom::{
    error::{Error, ErrorKind, ParseError},
    combinator::opt,
    IResult,
};

#[derive(Clone, Debug, PartialEq)]
pub struct TokenSlice<'a>(pub &'a [SpannedToken<'a, CoreToken>]);

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
    },
    Paragraph {
        content: String,
    },
    CodeBlock {
        language: Option<String>,
        tokens: Vec<(CoreToken, std::ops::Range<usize>)>,
    },
}

fn match_token<'a, E: ParseError<TokenSlice<'a>>>(
    expected: CoreToken,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, SpannedToken<'a, CoreToken>, E> {
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

fn any_token<'a, E: ParseError<TokenSlice<'a>>>(
    i: TokenSlice<'a>,
) -> IResult<TokenSlice<'a>, SpannedToken<'a, CoreToken>, E> {
    if i.0.is_empty() {
        Err(nom::Err::Error(E::from_error_kind(i, ErrorKind::Eof)))
    } else {
        Ok((TokenSlice(&i.0[1..]), i.0[0].clone()))
    }
}

fn take_until_newline<'a, E: ParseError<TokenSlice<'a>>>(
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, &'a [SpannedToken<'a, CoreToken>], E> {
    move |i: TokenSlice<'a>| {
        for (idx, token) in i.0.iter().enumerate() {
            if token.0 == CoreToken::Newline {
                return Ok((TokenSlice(&i.0[idx..]), &i.0[..idx]));
            }
        }
        Ok((TokenSlice(&[]), i.0))
    }
}

fn reconstruct_string(tokens: &[SpannedToken<'_, CoreToken>], source: &str) -> String {
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
        let (i, first_token) = match_token(CoreToken::HeadingMarker)(i)?;
        let first_str = &source[first_token.1.clone()];

        let level = first_str.chars().take_while(|&c| c == '#').count();
        if level < 1 || level > 6 {
            return Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Tag)));
        }

        let (i, content_tokens) = take_until_newline()(i)?;
        let (i, _) = opt(match_token(CoreToken::Newline))(i)?;

        Ok((
            i,
            MdNode::Heading {
                level: level as u8,
                content: reconstruct_string(content_tokens, source).trim().to_string(),
            },
        ))
    }
}

pub fn parse_codeblock<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, MdNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (mut i, start_tok) = match_token(CoreToken::CodeBlockStart)(i)?;
        let start_str = &source[start_tok.1.clone()];
        let mut lang = start_str[3..].trim().to_string();

        if lang.is_empty() {
            if let Ok((i_next, next_tok)) = any_token::<Error<_>>(i.clone()) {
                if next_tok.0 != CoreToken::Newline {
                    lang = source[next_tok.1.clone()].trim().to_string();
                    i = i_next;
                }
            }
        }

        let mut inner_tokens = vec![];
        loop {
            if i.0.is_empty() {
                break;
            }
            if i.0[0].0 == CoreToken::CodeBlockEnd {
                i = TokenSlice(&i.0[1..]);
                break;
            }
            inner_tokens.push(i.0[0].clone());
            i = TokenSlice(&i.0[1..]);
        }

        Ok((
            i,
            MdNode::CodeBlock {
                language: if lang.is_empty() { None } else { Some(lang) },
                tokens: inner_tokens.into_iter().map(|(t, r)| (t, r)).collect(),
            },
        ))
    }
}

pub fn parse_paragraph<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, MdNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let mut i = i;
        let mut content_tokens = vec![];

        if i.0.is_empty() {
            return Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Eof)));
        }
        if i.0[0].0 == CoreToken::Newline {
            return Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Tag)));
        }
        if i.0[0].0 == CoreToken::HeadingMarker || i.0[0].0 == CoreToken::CodeBlockStart {
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
                if next_tok.0 == CoreToken::HeadingMarker || next_tok.0 == CoreToken::CodeBlockStart {
                    break;
                }
            }

            content_tokens.push(i.0[0].clone());
            i = TokenSlice(&i.0[1..]);
        }

        while !i.0.is_empty() && i.0[0].0 == CoreToken::Newline {
            i = TokenSlice(&i.0[1..]);
        }

        Ok((
            i,
            MdNode::Paragraph {
                content: reconstruct_string(&content_tokens, source).trim().to_string(),
            },
        ))
    }
}

pub fn parse_markdown<'a>(
    source: &'a str,
    tokens: &'a [SpannedToken<'a, CoreToken>],
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
