use rust_decimal::Decimal;
use rustledger_core::{Open, directive::Directive, position::Position};
use rustledger_loader::{LoadResult, Loader};
use rustledger_query::executor::Executor;
use rustledger_query::parse;
use std::{collections::HashMap, collections::hash_map::Entry, path::PathBuf};

struct Acknowledgement {
    name: String,
    email: Option<String>,
    donations: Vec<Donation>,
}

struct Donation {
    amount: Position,
    narration: String,
}

pub struct Acknowledgements {
    acknowledgements: HashMap<String, Acknowledgement>,
}

fn extract_donors(result: &LoadResult) -> HashMap<String, Acknowledgement> {
    let mut donors = HashMap::new();
    for directive in &result.directives {
        if let Directive::Open(open) = &directive.value {
            if open.account.starts_with("Income:Donor:") {
                let account = open.account.to_string();
                donors
                    .entry(account)
                    .or_insert_with(|| {
                        let name = extract_name(&open);
                        let email = open.meta.get("email").map(|value| value.to_string());
                        Acknowledgement {
                            name,
                            email,
                            donations: vec![],
                        }
                    });
            }
        }
    }
    donors
}

fn extract_unacknowledged_donations(result: &LoadResult) -> Result<Vec<(String, Donation)>, Box<dyn std::error::Error>> {
    let mut donations = Vec::new();
        let mut executor = Executor::new_with_sources(&result.directives, &result.source_map);
        let query = parse(
            "
            SELECT account, narration, position
            WHERE 'acknowledged' NOT IN tags
            AND account ~ '^Income:Donor:'
            ORDER BY account ASC
            ",
        )?;
        let query_result = executor.execute(&query)?;
    Ok(donations)
}

impl Acknowledgements {
    pub fn from_beancount(path: PathBuf) -> Result<Self, Box<dyn std::error::Error>> {
        let result = Loader::new().load(&path)?;
        let mut acknowledgements = Acknowledgements {
            acknowledgements: extract_donors(&result),
        };
        extract_unacknowledged_donations(&result)?;
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
    fn test_from_beancount_skips_acknowledged_transactions() {
        let test_cases = ["standalone.beancount", "split.beancount"];

        for filename in test_cases {
            let path = get_test_data_path(filename);
            let got = Acknowledgements::from_beancount(path);
            for (account, acknowledgement) in got
                .expect("expected to load test data successfully")
                .acknowledgements
            {
                // All of John's donations have been acknowledged.
                if account == "Income:Donor:DoeJohn" {
                    assert_eq!(acknowledgement.donations.len(), 0);
                }
            }
        }
    }
}
