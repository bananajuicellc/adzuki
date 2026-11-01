use rustledger_core::{Open, directive::Directive, NaiveDate, position::Position};
use rustledger_loader::{LoadResult, Loader};
use rustledger_query::parse;
use rustledger_query::{Value, executor::Executor};
use std::{collections::HashMap, path::PathBuf};

mod email;

pub struct Acknowledgement {
    pub name: String,
    pub email: Option<String>,
    pub donations: Vec<Donation>,
}

pub struct Donation {
    pub date: NaiveDate,
    pub amount: Box<Position>,
    pub narration: String,
}

pub struct Acknowledgements {
    pub acknowledgements: HashMap<String, Acknowledgement>,
}

fn extract_donors(result: &LoadResult) -> HashMap<String, Acknowledgement> {
    let mut donors = HashMap::new();
    for directive in &result.directives {
        if let Directive::Open(open) = &directive.value {
            if open.account.starts_with("Income:Donor:") {
                let account = open.account.to_string();
                donors.entry(account).or_insert_with(|| {
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

fn extract_unacknowledged_donations(
    result: &LoadResult,
) -> anyhow::Result<Vec<(String, Donation)>> {
    let mut donations = Vec::new();
    let mut executor = Executor::new_with_sources(&result.directives, &result.source_map);
    let query = parse(
        "
            SELECT account, date, narration, position
            WHERE 'acknowledged' NOT IN tags
            AND account ~ '^Income:Donor:'
            ORDER BY account ASC
            ",
    )?;
    let mut query_result = executor.execute(&query)?;
    for row in query_result.rows.iter_mut() {
        let position = row.pop();
        let narration = row.pop();
        let date = row.pop();
        let account = row.pop();

        if let Some(Value::String(account)) = &account
            && let Some(Value::String(narration)) = &narration
            && let Some(Value::Date(date)) = &date
            && let Some(Value::Position(position)) = &position
        {
            donations.push((
                account.clone(),
                Donation {
                    date: date.clone(),
                    amount: position.clone(),
                    narration: narration.clone(),
                },
            ));
        } else {
            return Err(anyhow::anyhow!(
                "Invalid account {:?}, date {:?}, narration {:?}, or position {:?}",
                account,
                date,
                narration,
                position
            ));
        }
    }
    Ok(donations)
}

impl Acknowledgements {
    pub fn from_beancount(path: PathBuf) -> anyhow::Result<Self> {
        let result = Loader::new().load(&path)?;
        let mut acknowledgements = extract_donors(&result);
        let donations = extract_unacknowledged_donations(&result)?;
        for (account, donation) in donations {
            match acknowledgements.get_mut(&account) {
                Some(acknowledgement) => {
                    acknowledgement.donations.push(donation);
                },
                None => { return Err(anyhow::anyhow!("Missing metadata for account {}", account)); },
            }
        }
        // Delete all entries with no donations.
        acknowledgements.retain(|_, value| value.donations.len() != 0);
        Ok(Acknowledgements { acknowledgements })
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
    fn test_from_beancount_removes_empty_acknowledgements() {
        let test_cases = ["standalone.beancount", "split.beancount"];
        for filename in test_cases {
            let path = get_test_data_path(filename);
            let got = Acknowledgements::from_beancount(path);
            for (_, acknowledgement) in got
                .expect("expected to load test data successfully")
                .acknowledgements
            {
                // All empty acknowledgements should be removed.
                assert_ne!(acknowledgement.donations.len(), 0);
            }
        }
    }

    #[test]
    fn test_from_beancount_has_expected_number_of_unacknowledged_donations() {
        let mut expected = HashMap::new();
        expected.insert("Income:Donor:PérezJuan", 3);
        expected.insert("Income:Donor:SmithJane", 2);
        expected.insert("Income:Donor:KowalskiJan", 1);
        let test_cases = ["standalone.beancount", "split.beancount"];

        for filename in test_cases {
            let path = get_test_data_path(filename);
            let got = Acknowledgements::from_beancount(path);
            let acknowledgements =  got
                .expect("expected to load test data successfully")
                .acknowledgements;
            assert_eq!(expected.len(), acknowledgements.len());
            for (key, count) in &expected {
                assert_eq!(*count, acknowledgements[*key].donations.len());
            }
        }
    }
}
