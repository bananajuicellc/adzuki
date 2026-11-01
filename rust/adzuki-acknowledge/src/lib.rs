use rust_decimal::Decimal;
use rustledger_core::{Open, directive::Directive, position::Position};
use rustledger_loader::Loader;
use rustledger_query::executor::Executor;
use rustledger_query::parse;
use std::{collections::HashMap, path::PathBuf};

struct DonorMetadata {
    name: String,
    email: Option<String>,
}

struct Donation {
    amount: Position,
    narration: String,
    donor_account: String,
}

struct Acknowledgement {
    donor: DonorMetadata,
    donations: Vec<Donation>,
}

pub struct Acknowledgements {
    donors: HashMap<String, DonorMetadata>,
    acknowledgements: Vec<Acknowledgement>,
}

impl Acknowledgements {
    pub fn from_beancount(path: PathBuf) -> Result<Self, Box<dyn std::error::Error>> {
        let result = Loader::new().load(&path)?;
        let mut acknowledgements = Acknowledgements { donors: HashMap::new(), acknowledgements: vec![] };
        for directive in &result.directives {
            if let Directive::Open(open) = &directive.value {
                if open.account.starts_with("Income:Donor:") {
                    let donor = DonorMetadata {
                        name: extract_name(&open),
                        email: open.meta.get("email").map(|value| value.to_string()),
                    };
                    acknowledgements.donors.insert(open.account.to_string(), donor);
                }
            }
        }
        let mut executor= Executor::new_with_sources(
            &result.directives,
            &result.source_map,
        );
        let query = parse(
            "
            SELECT account, narration, position
            WHERE 'acknowledged' NOT IN tags
            AND account ~ '^Income:Donor:'
            ")?;
        let query_result= executor.execute(&query)?;
        println!("{:?}", query_result);
        Ok(acknowledgements)
    }
}

fn extract_name(open: &Open) -> String {
    open.account.to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn get_test_data_path(filename: &str) -> PathBuf {
        // Safely anchors path to the project root directory
        let mut path = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
        path.push("tests");
        path.push("data");
        path.push(filename);
        path
    }

    #[test]
    fn test_extract_donation_postings() {
        let path = get_test_data_path("standalone.beancount");
        let got = Acknowledgements::from_beancount(path);
    }
}
