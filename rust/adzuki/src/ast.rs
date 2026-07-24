use uniffi;

#[derive(uniffi::Record, Debug, Clone)]
pub struct Span {
    pub start: u32,
    pub end: u32,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct DirectiveWrapper {
    pub directive: BeancountNode,
    pub directive_span: Span,
    pub comments: Vec<AstNode>,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct ParseTree {
    pub nodes: Vec<DirectiveWrapper>,
}

#[derive(uniffi::Enum, Debug, Clone)]
pub enum AstNode {
    Heading { level: u8, content: String, span: Span },
    Paragraph { content: String, span: Span },
    CodeBlock { content: String, span: Span },
}

#[derive(uniffi::Enum, Debug, Clone)]
pub enum BeancountNode {
    Empty,
    IncludeDirective { file: String },
    OptionDirective { name: String, value: String },
    OpenDirective { date: String, account: String, currencies: Vec<String>, booking_method: Option<String> },
    CloseDirective { date: String, account: String },
    Transaction { date: String, flag: String, payee: Option<String>, narration: Option<String>, postings: Vec<Posting> },
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct Posting {
    pub flag: Option<String>,
    pub account: String,
    pub amount: Option<Amount>,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct Amount {
    pub number: String,
    pub currency: String,
}
