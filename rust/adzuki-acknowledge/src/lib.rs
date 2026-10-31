use rust_decimal::Decimal;
use rustledger_core::{Open, directive::Directive};
use rustledger_loader::Loader;
use std::{collections::HashMap, path::PathBuf};

struct DonorMetadata {
    name: String,
    email: Option<String>,
}

struct Donation {
    amount: Decimal,
    narration: String,
    donor_account: String,
}

struct Acknowledgement {
    donor: DonorMetadata,
    donations: Vec<Donation>,
}

pub struct Acknowledgements {
    donors: HashMap<String, DonorMetadata>,
    donations: Vec<Donation>,
}

impl Acknowledgements {
    pub fn from_beancount(path: PathBuf) -> Result<Self, Box<dyn std::error::Error>> {
        let result = Loader::new().load(&path)?;
        for directive in result.directives {
            if let Directive::Open(open) = directive.value {
                if open.account.starts_with("Income:Donor:") {
                    let donor = DonorMetadata {

                    }
                }
            }
        }
        Ok(Acknowledgements { donors: HashMap::new(), donations: vec![] })
    }
}

fn extract_name(open: &Open, account: String) -> String {

}
