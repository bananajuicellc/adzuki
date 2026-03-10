use uniffi;

#[derive(uniffi::Record, Debug, Clone)]
pub struct ParseTree {
    pub nodes: Vec<AstNode>,
}

#[derive(uniffi::Enum, Debug, Clone)]
pub enum AstNode {
    Heading { level: u8, content: String },
    Paragraph { content: String },
    CodeBlock { content: String },
}
