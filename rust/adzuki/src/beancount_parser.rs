use crate::ast::{BeancountNode, Amount, Posting, DirectiveWrapper, AstNode};
use crate::parser::parse_markdown;
use crate::lexer::lex_core;
use crate::lexer::{BeancountToken, SpannedToken};
use nom::{
    error::{Error, ErrorKind, ParseError},
    IResult,
};

#[derive(Debug, Clone, PartialEq)]
pub struct BeancountParseError {
    pub span: std::ops::Range<usize>,
    pub message: String,
}


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
        while !i.0.is_empty() && (i.0[0].0 == BeancountToken::Whitespace || i.0[0].0 == BeancountToken::Newline) {
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


pub fn parse_include_directive<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, BeancountNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, _) = skip_whitespace()(i)?;
        let (i, _) = match_token(BeancountToken::IncludeDirective)(i)?;
        let (i, _) = skip_inline_whitespace()(i)?;
        let (i, file_tok) = match_token(BeancountToken::StringLiteral)(i)?;

        Ok((
            i,
            BeancountNode::IncludeDirective {
                file: extract_string(&file_tok, source),
            },
        ))
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

pub fn parse_close_directive<'a>(
    source: &'a str,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, BeancountNode, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, _) = skip_whitespace()(i)?;
        let (i, date_tok) = match_token(BeancountToken::Date)(i)?;
        let (i, _) = skip_inline_whitespace()(i)?;
        let (i, _) = match_token(BeancountToken::CloseDirective)(i)?;
        let (i, _) = skip_inline_whitespace()(i)?;
        let (i, acc_tok) = match_token(BeancountToken::Account)(i)?;

        Ok((
            i,
            BeancountNode::CloseDirective {
                date: source[date_tok.1.clone()].to_string(),
                account: source[acc_tok.1.clone()].to_string(),
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
                if i_peak.0.is_empty() || i_peak.0[0].0 == BeancountToken::Date || i_peak.0[0].0 == BeancountToken::OptionDirective || i_peak.0[0].0 == BeancountToken::IncludeDirective || i_peak.0[0].0 == BeancountToken::Newline {
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

fn process_comment_block(source: &str, comment_spans: &[std::ops::Range<usize>]) -> Vec<AstNode> {
    if comment_spans.is_empty() {
        return vec![];
    }

    // We recreate the markdown string from the comments, replacing `; ` or `;` with spaces.
    let mut comment_source = String::new();
    let mut byte_offsets = vec![];

    for span in comment_spans {
        let raw = &source[span.clone()];
        let stripped = if raw.starts_with("; ") {
            &raw[2..]
        } else if raw.starts_with(";") {
            &raw[1..]
        } else {
            raw
        };

        let start_offset = comment_source.len();
        comment_source.push_str(stripped);
        comment_source.push('\n'); // Add newline since we process line by line

        let end_offset = comment_source.len();
        let original_start = span.start + (raw.len() - stripped.len());

        byte_offsets.push((start_offset..end_offset, original_start));
    }

    let core_tokens = lex_core(&comment_source);
    let mut md_nodes = parse_markdown(&comment_source, &core_tokens);

    // Translate the spans back to the original source spans
    for node in &mut md_nodes {
        let (start, end) = match node {
            crate::parser::MdNode::Heading { span, .. } => (span.start, span.end),
            crate::parser::MdNode::Paragraph { span, .. } => (span.start, span.end),
            crate::parser::MdNode::CodeBlock { span, .. } => (span.start, span.end),
        };

        // Find which line it belongs to
        let mut new_start = 0;
        let mut new_end = 0;

        for (local_range, orig_start) in &byte_offsets {
            if local_range.contains(&start) {
                new_start = orig_start + (start - local_range.start);
            }
            // we use <= because end is exclusive
            if local_range.start <= end && end <= local_range.end {
                new_end = orig_start + (end - local_range.start);
            }
        }

        *node = match node.clone() {
            crate::parser::MdNode::Heading { level, content, .. } => crate::parser::MdNode::Heading { level, content, span: (new_start..new_end) },
            crate::parser::MdNode::Paragraph { content, .. } => crate::parser::MdNode::Paragraph { content, span: (new_start..new_end) },
            crate::parser::MdNode::CodeBlock { language, tokens, .. } => crate::parser::MdNode::CodeBlock { language, tokens, span: (new_start..new_end) },
        };
    }

    let mut ast_nodes = vec![];
    for node in md_nodes {
         match node {
            crate::parser::MdNode::Heading { level, content, span } => {
                ast_nodes.push(AstNode::Heading {
                    level,
                    content,
                    span: crate::ast::Span { start: span.start as u32, end: span.end as u32 }
                });
            }
            crate::parser::MdNode::Paragraph { content, span } => {
                ast_nodes.push(AstNode::Paragraph {
                    content,
                    span: crate::ast::Span { start: span.start as u32, end: span.end as u32 }
                });
            }
            crate::parser::MdNode::CodeBlock { language: _, tokens: _, span } => {
                 ast_nodes.push(AstNode::CodeBlock {
                    content: "".to_string(), // we don't fully reconstruct inner codeblocks inside comments for now to keep it simple, it wasn't requested
                    span: crate::ast::Span { start: span.start as u32, end: span.end as u32 }
                });
            }
        }
    }

    ast_nodes
}

pub fn parse_beancount<'a>(
    source: &'a str,
    tokens: &'a [SpannedToken<BeancountToken>],
) -> (Vec<DirectiveWrapper>, Vec<BeancountParseError>) {
    let mut wrappers = vec![];
    let mut errors = vec![];
    let mut i = TokenSlice(tokens);
    let mut current_comments = vec![];

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

        if i.0[0].0 == BeancountToken::Comment {
            current_comments.push(i.0[0].1.clone());
            i = TokenSlice(&i.0[1..]);
            continue;
        }

        let start_span = i.0[0].1.start;
        let mut parsed_node = None;
        let mut parsed_i = i.clone();

        if let Ok((next_i, node)) = parse_include_directive(source)(i.clone()) {
            parsed_node = Some(node);
            parsed_i = next_i;
        } else if let Ok((next_i, node)) = parse_option_directive(source)(i.clone()) {
            parsed_node = Some(node);
            parsed_i = next_i;
        } else if !i.0.is_empty() && i.0[0].0 == BeancountToken::Date {
            if let Ok((next_i, node)) = parse_open_directive(source)(i.clone()) {
                parsed_node = Some(node);
                parsed_i = next_i;
            } else if let Ok((next_i, node)) = parse_close_directive(source)(i.clone()) {
                parsed_node = Some(node);
                parsed_i = next_i;
            } else if let Ok((next_i, node)) = parse_transaction(source)(i.clone()) {
                parsed_node = Some(node);
                parsed_i = next_i;
            }
        }

        if let Some(node) = parsed_node {
            let end_span = if parsed_i.0.is_empty() {
                source.len()
            } else {
                parsed_i.0[0].1.start
            };

            let comments_ast = process_comment_block(source, &current_comments);
            current_comments.clear();

            wrappers.push(DirectiveWrapper {
                directive: node,
                directive_span: crate::ast::Span { start: start_span as u32, end: end_span as u32 },
                comments: comments_ast,
            });
            i = parsed_i;
            continue;
        }

        errors.push(BeancountParseError {
            span: i.0[0].1.clone(),
            message: format!("Unexpected token: {:?}", i.0[0].0),
        });

        // skip one token if parse fails to prevent infinite loop
        i = TokenSlice(&i.0[1..]);
    }

    if !current_comments.is_empty() {
        let comments_ast = process_comment_block(source, &current_comments);
        if !comments_ast.is_empty() {
            let span = crate::ast::Span {
                 start: current_comments.first().unwrap().start as u32,
                 end: current_comments.last().unwrap().end as u32
            };
            wrappers.push(DirectiveWrapper {
                directive: BeancountNode::Empty,
                directive_span: span,
                comments: comments_ast,
            });
        }
    }

    (wrappers, errors)
}
