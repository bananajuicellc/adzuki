use adzuki::parse_markdown;
use std::fs;

#[test]
fn test_parse_headings_markdown() {
    let source = fs::read_to_string("tests/fixtures/headings.md").unwrap();
    let cst = parse_markdown(&source);

    let cst_str = format!("{}", cst);
    assert!(cst_str.contains("Heading1 \"# \""));
    assert!(cst_str.contains("Text \"Heading\""));
    assert!(cst_str.contains("Text \"1\""));
    assert!(cst_str.contains("Heading2 \"## \""));
    assert!(cst_str.contains("Text \"Heading\""));
    assert!(cst_str.contains("Text \"2\""));
}

#[test]
fn test_parse_beancount_block() {
    let source = std::fs::read_to_string("rust/adzuki/tests/fixtures/beancount.md").unwrap_or_else(|_| std::fs::read_to_string("tests/fixtures/beancount.md").unwrap());
    let tree = adzuki::parse_to_tree(source);

    let beancount_node = tree.nodes.iter().find(|n| matches!(n, adzuki::ast::AstNode::Beancount { .. })).unwrap();

    if let adzuki::ast::AstNode::Beancount { nodes } = beancount_node {
        assert_eq!(nodes.len(), 3);

        match &nodes[0] {
            adzuki::ast::BeancountNode::OptionDirective { name, value } => {
                assert_eq!(name, "title");
                assert_eq!(value, "Test Book");
            }
            _ => panic!("Expected OptionDirective"),
        }

        match &nodes[1] {
            adzuki::ast::BeancountNode::OpenDirective { date, account, currencies, .. } => {
                assert_eq!(date, "2024-01-01");
                assert_eq!(account, "Assets:Checking");
                assert_eq!(currencies[0], "USD");
            }
            _ => panic!("Expected OpenDirective"),
        }

        match &nodes[2] {
            adzuki::ast::BeancountNode::Transaction { date, flag, postings, .. } => {
                assert_eq!(date, "2024-01-02");
                assert_eq!(flag, "*");
                assert_eq!(postings.len(), 2);
                assert_eq!(postings[0].account, "Assets:Checking");
                assert_eq!(postings[0].amount.as_ref().unwrap().number, "-50.00");
                assert_eq!(postings[0].amount.as_ref().unwrap().currency, "USD");
            }
            _ => panic!("Expected Transaction"),
        }
    } else {
        panic!("Expected Beancount node");
    }
}

#[test]
fn test_parse_code_blocks_markdown() {
    let source = fs::read_to_string("tests/fixtures/code_blocks.md").unwrap();
    let cst = parse_markdown(&source);

    let cst_str = format!("{}", cst);
    assert!(cst_str.contains("CodeBlock \"```rust\\nfn main() {\\n    println!(\\\"Hello, world!\\\");\\n}\\n```\""));
    assert!(cst_str.contains("Text \"This\""));
    assert!(cst_str.contains("Text \"is\""));
    assert!(cst_str.contains("Text \"a\""));
    assert!(cst_str.contains("Text \"paragraph\""));
    assert!(cst_str.contains("Text \"before\""));
    assert!(cst_str.contains("Text \"the\""));
    assert!(cst_str.contains("Text \"code\""));
    assert!(cst_str.contains("Text \"block.\""));
}
