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
    Beancount { nodes: Vec<BeancountNode> },
}

#[derive(uniffi::Enum, Debug, Clone)]
pub enum BeancountNode {
    OptionDirective { name: String, value: String },
    OpenDirective { date: String, account: String, currencies: Vec<String>, booking_method: Option<String> },
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
