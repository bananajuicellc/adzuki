use crate::ast::{BeancountNode, Amount, Posting};
use crate::lexer::{BeancountToken, SpannedToken};
use nom::{
    error::{Error, ErrorKind, ParseError},
    IResult,
};

#[derive(Clone, Debug, PartialEq)]
pub struct TokenSlice<'a>(pub &'a [SpannedToken<BeancountToken>]);

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

pub fn match_token<'a, E: ParseError<TokenSlice<'a>>>(
    expected: BeancountToken,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, SpannedToken<BeancountToken>, E> {
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

pub fn skip_whitespace<'a, E: ParseError<TokenSlice<'a>>>(
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, (), E> {
    move |mut i: TokenSlice<'a>| {
        while !i.0.is_empty() && (i.0[0].0 == BeancountToken::Whitespace || i.0[0].0 == BeancountToken::Newline || i.0[0].0 == BeancountToken::Comment) {
            i = TokenSlice(&i.0[1..]);
        }
        Ok((i, ()))
    }
}

pub fn skip_inline_whitespace<'a, E: ParseError<TokenSlice<'a>>>(
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, (), E> {
    move |mut i: TokenSlice<'a>| {
        while !i.0.is_empty() && (i.0[0].0 == BeancountToken::Whitespace || i.0[0].0 == BeancountToken::Comment) {
            i = TokenSlice(&i.0[1..]);
        }
        Ok((i, ()))
    }
}

fn extract_string(tok: &SpannedToken<BeancountToken>, source: &str) -> String {
    let raw = &source[tok.1.clone()];
    if raw.starts_with('"') && raw.ends_with('"') && raw.len() >= 2 {
        raw[1..raw.len()-1].to_string()
    } else {
        raw.to_string()
    }
}

pub fn parse_option_directive<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, BeancountNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, _) = skip_whitespace()(i)?;
        let (i, _) = match_token(BeancountToken::OptionDirective)(i)?;
        let (i, _) = skip_inline_whitespace()(i)?;
        let (i, name_tok) = match_token(BeancountToken::StringLiteral)(i)?;
        let (i, _) = skip_inline_whitespace()(i)?;
        let (i, val_tok) = match_token(BeancountToken::StringLiteral)(i)?;

        Ok((
            i,
            BeancountNode::OptionDirective {
                name: extract_string(&name_tok, source),
                value: extract_string(&val_tok, source),
            },
        ))
    }
}

pub fn parse_open_directive<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, BeancountNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, _) = skip_whitespace()(i)?;
        let (i, date_tok) = match_token(BeancountToken::Date)(i)?;
        let (i, _) = skip_inline_whitespace()(i)?;
        let (i, _) = match_token(BeancountToken::OpenDirective)(i)?;
        let (i, _) = skip_inline_whitespace()(i)?;
        let (i, acc_tok) = match_token(BeancountToken::Account)(i)?;

        let mut i = i;
        let mut currencies = vec![];
        let mut booking_method = None;

        let (mut i_next, _) = skip_inline_whitespace()(i.clone())?;
        while !i_next.0.is_empty() {
            if let Ok((i_cur, cur_tok)) = match_token::<Error<_>>(BeancountToken::Currency)(i_next.clone()) {
                currencies.push(source[cur_tok.1.clone()].to_string());
                let (i_comma, _) = skip_inline_whitespace()(i_cur.clone())?;
                if let Ok((i_after_comma, _)) = match_token::<Error<_>>(BeancountToken::Comma)(i_comma.clone()) {
                    let (i_after_comma_ws, _) = skip_inline_whitespace()(i_after_comma)?;
                    i_next = i_after_comma_ws;
                } else {
                    i_next = i_comma;
                }
                i = i_next.clone();
            } else if let Ok((i_str, str_tok)) = match_token::<Error<_>>(BeancountToken::StringLiteral)(i_next.clone()) {
                booking_method = Some(extract_string(&str_tok, source));
                i = i_str;
                break;
            } else {
                break;
            }
        }

        Ok((
            i,
            BeancountNode::OpenDirective {
                date: source[date_tok.1.clone()].to_string(),
                account: source[acc_tok.1.clone()].to_string(),
                currencies,
                booking_method,
            },
        ))
    }
}

pub fn parse_posting<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, Posting, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, _) = skip_inline_whitespace()(i)?;

        let mut i = i;
        let mut flag = None;
        if let Ok((i_flag, flag_tok)) = match_token::<Error<_>>(BeancountToken::TxnFlag)(i.clone()) {
            flag = Some(source[flag_tok.1.clone()].to_string());
            let (i_ws, _) = skip_inline_whitespace()(i_flag)?;
            i = i_ws;
        }

        let (i, acc_tok) = match_token(BeancountToken::Account)(i)?;
        let account = source[acc_tok.1.clone()].to_string();

        let (mut i_ws, _) = skip_inline_whitespace()(i)?;

        let mut amount = None;
        if let Ok((i_num, num_tok)) = match_token::<Error<_>>(BeancountToken::Number)(i_ws.clone()) {
            let (i_num_ws, _) = skip_inline_whitespace()(i_num)?;
            if let Ok((i_cur, cur_tok)) = match_token::<Error<_>>(BeancountToken::Currency)(i_num_ws.clone()) {
                amount = Some(Amount {
                    number: source[num_tok.1.clone()].to_string(),
                    currency: source[cur_tok.1.clone()].to_string(),
                });
                i_ws = i_cur;
            }
        }

        let mut i_final = i_ws;
        while !i_final.0.is_empty() && (i_final.0[0].0 == BeancountToken::Whitespace || i_final.0[0].0 == BeancountToken::Comment) {
            i_final = TokenSlice(&i_final.0[1..]);
        }

        if !i_final.0.is_empty() && i_final.0[0].0 == BeancountToken::Newline {
            i_final = TokenSlice(&i_final.0[1..]);
        }

        Ok((i_final, Posting { flag, account, amount }))
    }
}

pub fn parse_transaction<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, BeancountNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, _) = skip_whitespace()(i)?;
        let (i, date_tok) = match_token(BeancountToken::Date)(i)?;
        let (i, _) = skip_inline_whitespace()(i)?;

        let mut i = i;
        if let Ok((i_flag, _flag_tok)) = match_token::<Error<_>>(BeancountToken::TxnFlag)(i.clone()) {
            let flag = source[_flag_tok.1.clone()].to_string();
            i = i_flag;

            let (i, _) = skip_inline_whitespace()(i)?;

            let mut i = i;
            let mut strings = vec![];
            while let Ok((i_str, str_tok)) = match_token::<Error<_>>(BeancountToken::StringLiteral)(i.clone()) {
                strings.push(extract_string(&str_tok, source));
                let (i_ws, _) = skip_inline_whitespace()(i_str)?;
                i = i_ws;
            }

            let payee = if strings.len() > 1 { Some(strings[0].clone()) } else { None };
            let narration = if strings.len() > 1 { Some(strings[1].clone()) } else if strings.len() == 1 { Some(strings[0].clone()) } else { None };

            while !i.0.is_empty() && i.0[0].0 != BeancountToken::Newline {
                i = TokenSlice(&i.0[1..]);
            }
            if !i.0.is_empty() && i.0[0].0 == BeancountToken::Newline {
                i = TokenSlice(&i.0[1..]);
            }

            let mut postings = vec![];
            loop {
                let mut i_peak = i.clone();

                if !i_peak.0.is_empty() && i_peak.0[0].0 == BeancountToken::Whitespace {
                    i_peak = TokenSlice(&i_peak.0[1..]);
                }
                if i_peak.0.is_empty() || i_peak.0[0].0 == BeancountToken::Date || i_peak.0[0].0 == BeancountToken::OptionDirective || i_peak.0[0].0 == BeancountToken::Newline {
                    break;
                }

                if let Ok((i_next, posting)) = parse_posting(source)(i.clone()) {
                    postings.push(posting);
                    i = i_next;
                } else {
                    break;
                }
            }

            Ok((
                i,
                BeancountNode::Transaction {
                    date: source[date_tok.1.clone()].to_string(),
                    flag,
                    payee,
                    narration,
                    postings,
                },
            ))
        } else {
            Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Tag)))
        }
    }
}

pub fn parse_beancount<'a>(
    source: &'a str,
    tokens: &'a [SpannedToken<BeancountToken>],
) -> Vec<BeancountNode> {
    let mut nodes = vec![];
    let mut i = TokenSlice(tokens);

    while !i.0.is_empty() {
        if let Ok((i_next, _)) = skip_whitespace::<Error<_>>()(i.clone()) {
            if i_next.0.is_empty() {
                break;
            }
            i = i_next;
        }

        if i.0.is_empty() {
            break;
        }

        if let Ok((next_i, node)) = parse_option_directive(source)(i.clone()) {
            nodes.push(node);
            i = next_i;
            continue;
        }

        if !i.0.is_empty() && i.0[0].0 == BeancountToken::Date {
            if let Ok((next_i, node)) = parse_open_directive(source)(i.clone()) {
                nodes.push(node);
                i = next_i;
                continue;
            } else if let Ok((next_i, node)) = parse_transaction(source)(i.clone()) {
                nodes.push(node);
                i = next_i;
                continue;
            }
        }

        // skip one token if parse fails to prevent infinite loop
        i = TokenSlice(&i.0[1..]);
    }

    nodes
}
