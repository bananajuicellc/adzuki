use crate::query_lexer::{QueryToken, SpannedToken};
use crate::query_ast::{QueryStatement, Target, Expression, OrderBy};
use nom::{
    error::{Error, ErrorKind, ParseError},
    IResult,
    combinator::opt,
    multi::separated_list1,
    branch::alt,
};

#[derive(Clone, Debug, PartialEq)]
pub struct TokenSlice<'a>(pub &'a [SpannedToken<QueryToken>]);

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

pub fn skip_whitespace<'a, E: ParseError<TokenSlice<'a>>>() -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, (), E> {
    move |i: TokenSlice<'a>| {
        let mut idx = 0;
        while idx < i.0.len() && i.0[idx].0 == QueryToken::Whitespace {
            idx += 1;
        }
        Ok((TokenSlice(&i.0[idx..]), ()))
    }
}

fn match_token<'a, E: ParseError<TokenSlice<'a>>>(
    expected: QueryToken,
) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, SpannedToken<QueryToken>, E> {
    move |i: TokenSlice<'a>| {
        let (i, _) = skip_whitespace()(i)?;
        if i.0.is_empty() {
            Err(nom::Err::Error(E::from_error_kind(i, ErrorKind::Eof)))
        } else if std::mem::discriminant(&i.0[0].0) == std::mem::discriminant(&expected) {
            Ok((TokenSlice(&i.0[1..]), i.0[0].clone()))
        } else {
            Err(nom::Err::Error(E::from_error_kind(i, ErrorKind::Tag)))
        }
    }
}

pub fn parse_expression<'a>(source: &'a str) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, Expression, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, _) = skip_whitespace()(i)?;
        if i.0.is_empty() {
            return Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Eof)));
        }

        let first = &i.0[0].0;
        match first {
            QueryToken::Ident => {
                let tok = i.0[0].clone();
                let ident = source[tok.1].to_string();
                let mut i_next = TokenSlice(&i.0[1..]);

                let i_after_ws = skip_whitespace::<Error<_>>()(i_next.clone()).map(|(res, _)| res).unwrap_or(i_next.clone());
                if !i_after_ws.0.is_empty() && i_after_ws.0[0].0 == QueryToken::LParen {
                    i_next = TokenSlice(&i_after_ws.0[1..]);
                    let mut args = Vec::new();

                    let i_after_ws = skip_whitespace::<Error<_>>()(i_next.clone()).map(|(res, _)| res).unwrap_or(i_next.clone());
                    if !i_after_ws.0.is_empty() && i_after_ws.0[0].0 == QueryToken::RParen {
                        i_next = TokenSlice(&i_after_ws.0[1..]);
                    } else {
                        let (mut cur_i, arg1) = parse_expression(source)(i_next)?;
                        args.push(arg1);

                        loop {
                            let (after_comma, comma) = opt(match_token(QueryToken::Comma))(cur_i.clone())?;
                            if comma.is_some() {
                                let (after_arg, arg) = parse_expression(source)(after_comma)?;
                                args.push(arg);
                                cur_i = after_arg;
                            } else {
                                break;
                            }
                        }

                        let (after_rparen, _) = match_token(QueryToken::RParen)(cur_i)?;
                        i_next = after_rparen;
                    }

                    Ok((i_next, Expression::Function(ident, args)))
                } else {
                    Ok((i_next, Expression::Identifier(ident)))
                }
            },
            QueryToken::StringLiteral => {
                let tok = i.0[0].clone();
                let lit = source[tok.1].to_string();
                Ok((TokenSlice(&i.0[1..]), Expression::StringLiteral(lit)))
            },
            QueryToken::Number => {
                let tok = i.0[0].clone();
                let num = source[tok.1].to_string();
                Ok((TokenSlice(&i.0[1..]), Expression::Number(num)))
            },
            QueryToken::Date => {
                let tok = i.0[0].clone();
                let date = source[tok.1].to_string();
                Ok((TokenSlice(&i.0[1..]), Expression::Date(date)))
            },
            _ => Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Tag)))
        }
    }
}

pub fn parse_target<'a>(source: &'a str) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, Target, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, star) = opt(match_token(QueryToken::Star))(i.clone())?;
        if star.is_some() {
            return Ok((i, Target::Star));
        }

        let (i, expr) = parse_expression(source)(i)?;

        let (i, as_tok) = opt(match_token(QueryToken::As))(i.clone())?;
        if as_tok.is_some() {
            let (i, alias_tok) = match_token(QueryToken::Ident)(i)?;
            let alias = source[alias_tok.1].to_string();
            Ok((i, Target::Expression(expr, Some(alias))))
        } else {
            Ok((i, Target::Expression(expr, None)))
        }
    }
}

pub fn parse_condition<'a>(source: &'a str) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, Expression, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, left) = parse_expression(source)(i)?;

        let (i, op_tok) = alt((
            match_token(QueryToken::Eq),
            match_token(QueryToken::Neq),
            match_token(QueryToken::Lt),
            match_token(QueryToken::Lte),
            match_token(QueryToken::Gt),
            match_token(QueryToken::Gte),
            match_token(QueryToken::Tilde),
        ))(i)?;

        let op = source[op_tok.1].to_string();

        let (i, right) = parse_expression(source)(i)?;

        let mut cur_i = i.clone();
        let mut current_expr = Expression::BinaryOp(Box::new(left), op, Box::new(right));

        loop {
            let (after_op, bool_op) = alt((
                match_token::<Error<_>>(QueryToken::And),
                match_token::<Error<_>>(QueryToken::Or),
            ))(cur_i.clone()).unwrap_or((cur_i.clone(), (QueryToken::Whitespace, 0..0)));

            if bool_op.0 == QueryToken::And || bool_op.0 == QueryToken::Or {
                let op_str = source[bool_op.1].to_string();

                let (after_right_expr, next_left) = parse_expression(source)(after_op)?;

                let (after_cmp_op, cmp_op_tok) = alt((
                    match_token(QueryToken::Eq),
                    match_token(QueryToken::Neq),
                    match_token(QueryToken::Lt),
                    match_token(QueryToken::Lte),
                    match_token(QueryToken::Gt),
                    match_token(QueryToken::Gte),
                    match_token(QueryToken::Tilde),
                ))(after_right_expr)?;

                let cmp_op = source[cmp_op_tok.1].to_string();

                let (after_next_right, next_right) = parse_expression(source)(after_cmp_op)?;

                let next_binary = Expression::BinaryOp(Box::new(next_left), cmp_op, Box::new(next_right));
                current_expr = Expression::BinaryOp(Box::new(current_expr), op_str.to_uppercase(), Box::new(next_binary));
                cur_i = after_next_right;
            } else {
                break;
            }
        }

        Ok((cur_i, current_expr))
    }
}

pub fn parse_order_by<'a>(source: &'a str) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, OrderBy, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, expr) = parse_expression(source)(i)?;
        let (i, desc_tok) = opt(match_token::<Error<_>>(QueryToken::Desc))(i.clone())?;
        let (i, asc_tok) = opt(match_token::<Error<_>>(QueryToken::Asc))(i.clone())?;

        let desc = desc_tok.is_some() || (!asc_tok.is_some() && false); // default asc
        Ok((i, OrderBy { expr, desc }))
    }
}

pub fn parse_query<'a>(source: &'a str) -> impl FnMut(TokenSlice<'a>) -> IResult<TokenSlice<'a>, QueryStatement, Error<TokenSlice<'a>>> {
    move |i: TokenSlice<'a>| {
        let (i, first_token) = alt((
            match_token::<Error<_>>(QueryToken::Select),
            match_token::<Error<_>>(QueryToken::Balances),
            match_token::<Error<_>>(QueryToken::Print),
        ))(i)?;

        match first_token.0 {
            QueryToken::Select => {
                let (i, targets) = separated_list1(match_token(QueryToken::Comma), parse_target(source))(i)?;

                let (i, from_tok) = opt(match_token(QueryToken::From))(i.clone())?;
                let (i, from_clause) = if from_tok.is_some() {
                    let (i, cond) = parse_condition(source)(i)?;
                    (i, Some(cond))
                } else {
                    (i, None)
                };

                let (i, where_tok) = opt(match_token(QueryToken::Where))(i.clone())?;
                let (i, where_clause) = if where_tok.is_some() {
                    let (i, cond) = parse_condition(source)(i)?;
                    (i, Some(cond))
                } else {
                    (i, None)
                };

                let (i, group_tok) = opt(match_token(QueryToken::Group))(i.clone())?;
                let (i, group_by) = if group_tok.is_some() {
                    let (i, _) = match_token(QueryToken::By)(i)?;
                    let (i, groups) = separated_list1(match_token(QueryToken::Comma), parse_expression(source))(i)?;
                    (i, Some(groups))
                } else {
                    (i, None)
                };

                let (i, order_tok) = opt(match_token(QueryToken::Order))(i.clone())?;
                let (i, order_by) = if order_tok.is_some() {
                    let (i, _) = match_token(QueryToken::By)(i)?;
                    let (i, orders) = separated_list1(match_token(QueryToken::Comma), parse_order_by(source))(i)?;
                    (i, Some(orders))
                } else {
                    (i, None)
                };

                let (i, limit_tok) = opt(match_token(QueryToken::Limit))(i.clone())?;
                let (i, limit) = if limit_tok.is_some() {
                    let (i, limit_val_tok) = match_token(QueryToken::Number)(i)?;
                    let num_str = source[limit_val_tok.1].to_string();
                    let limit_val = num_str.parse::<usize>().unwrap_or(0);
                    (i, Some(limit_val))
                } else {
                    (i, None)
                };

                Ok((i, QueryStatement::Select {
                    targets,
                    from: from_clause,
                    where_clause,
                    group_by,
                    order_by,
                    limit,
                }))
            },
            QueryToken::Balances | QueryToken::Print => {
                let (i, from_tok) = opt(match_token(QueryToken::From))(i.clone())?;
                let (i, from_clause) = if from_tok.is_some() {
                    let (i, cond) = parse_condition(source)(i)?;
                    (i, Some(cond))
                } else {
                    (i, None)
                };

                let (i, where_tok) = opt(match_token(QueryToken::Where))(i.clone())?;
                let (i, where_clause) = if where_tok.is_some() {
                    let (i, cond) = parse_condition(source)(i)?;
                    (i, Some(cond))
                } else {
                    (i, None)
                };

                if first_token.0 == QueryToken::Balances {
                    Ok((i, QueryStatement::Balances {
                        from: from_clause,
                        where_clause,
                    }))
                } else {
                    Ok((i, QueryStatement::Print {
                        from: from_clause,
                        where_clause,
                    }))
                }
            },
            _ => Err(nom::Err::Error(Error::from_error_kind(i, ErrorKind::Tag))),
        }
    }
}
