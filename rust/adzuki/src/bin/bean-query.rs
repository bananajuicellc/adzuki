use std::env;
use std::fs;
use adzuki::query_lexer::lex_query;
use adzuki::query_parser::parse_query;
use adzuki::query_parser::TokenSlice;

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 {
        eprintln!("Usage: bean-query <file.beancount> [query]");
        std::process::exit(1);
    }

    let file_path = &args[1];

    // As of right now, we do not fully process the contents of the beancount file,
    // but we verify that the file exists and can be loaded.
    let _contents = match fs::read_to_string(file_path) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("Failed to read file '{}': {}", file_path, e);
            std::process::exit(1);
        }
    };

    let query = if args.len() > 2 {
        args[2..].join(" ")
    } else {
        println!("Interactive mode not fully implemented yet.");
        std::process::exit(0);
    };

    let tokens = lex_query(&query);
    let mut parser = parse_query(&query);
    match parser(TokenSlice(&tokens)) {
        Ok((rem, stmt)) => {
            if !rem.0.is_empty() {
                eprintln!("Warning: unparsed trailing tokens: {:?}", rem.0);
            }
            println!("Parsed Query AST:\n{:#?}", stmt);
        }
        Err(e) => {
            eprintln!("Failed to parse query: {:?}", e);
            std::process::exit(1);
        }
    }
}
