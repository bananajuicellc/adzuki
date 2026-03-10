pub mod lexer;
pub mod parser;

pub fn parse_markdown(source: &str) -> parser::Cst<'_> {
    let mut diags = vec![];
    let parser = parser::Parser::new(source, &mut diags);
    parser.parse(&mut diags)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_simple_markdown() {
        let source = "# Heading\nParagraph text.\n";
        let cst = parse_markdown(source);
        println!("{}", cst);
    }
}
