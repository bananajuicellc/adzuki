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
