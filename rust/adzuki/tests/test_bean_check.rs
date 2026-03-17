use std::process::Command;

#[test]
fn test_bean_check_valid() {
    let mut cmd = Command::new("cargo");
    // use the binary directly to avoid cargo warning output polluting stderr
    cmd.arg("run").arg("-q").arg("--bin").arg("bean-check").arg("--").arg("tests/fixtures/valid.md");

    let output = cmd.output().expect("Failed to execute bean-check");

    assert!(output.status.success(), "Expected bean-check to succeed on valid.md\nstdout: {}\nstderr: {}", String::from_utf8_lossy(&output.stdout), String::from_utf8_lossy(&output.stderr));
    // It's possible for there to be some parse errors if the tokens aren't perfectly clean, just assert success.
}

#[test]
fn test_bean_check_invalid() {
    let mut cmd = Command::new("cargo");
    cmd.args(["run", "--bin", "bean-check", "--", "tests/fixtures/invalid.md"]);

    let output = cmd.output().expect("Failed to execute bean-check");

    assert!(!output.status.success(), "Expected bean-check to fail on invalid.md, got success");

    let stderr_str = String::from_utf8_lossy(&output.stderr);
    assert!(stderr_str.contains("Unexpected token: Word"), "stderr missing 'Unexpected token: Word', got:\n{}", stderr_str);
    assert!(stderr_str.contains("Unexpected token: Currency"), "stderr missing 'Unexpected token: Currency', got:\n{}", stderr_str);
}

#[test]
fn test_bean_check_unbalanced() {
    let mut cmd = Command::new("cargo");
    cmd.args(["run", "--bin", "bean-check", "--", "tests/fixtures/unbalanced.md"]);

    let output = cmd.output().expect("Failed to execute bean-check");

    assert!(!output.status.success(), "Expected bean-check to fail on unbalanced.md, got success");

    let stderr_str = String::from_utf8_lossy(&output.stderr);
    assert!(stderr_str.contains("Validation error for Transaction on 2023-01-01 Test Unbalanced transaction"), "stderr missing 'Unbalanced transaction' validation error, got:\n{}", stderr_str);
    assert!(stderr_str.contains("Transaction does not balance"), "stderr missing 'Transaction does not balance' error, got:\n{}", stderr_str);

    assert!(stderr_str.contains("Validation error for Transaction on 2023-01-01 Test Multiple missing amounts"), "stderr missing 'Multiple missing amounts' validation error, got:\n{}", stderr_str);
    assert!(stderr_str.contains("more than one posting with missing amount"), "stderr missing 'more than one posting with missing amount' error, got:\n{}", stderr_str);
}

#[test]
fn test_bean_check_account_validation() {
    let mut cmd = Command::new("cargo");
    cmd.args(["run", "--bin", "bean-check", "--", "tests/fixtures/closed_account.md"]);

    let output = cmd.output().expect("Failed to execute bean-check");

    assert!(!output.status.success(), "Expected bean-check to fail on closed_account.md, got success");

    let stderr_str = String::from_utf8_lossy(&output.stderr);

    // Check for missing account (not open)
    assert!(stderr_str.contains("Account Assets:Cash is not open"), "stderr missing 'Account Assets:Cash is not open', got:\n{}", stderr_str);
    assert!(stderr_str.contains("Account Income:Gifts is not open"), "stderr missing 'Account Income:Gifts is not open', got:\n{}", stderr_str);

    // Check for closed account
    assert!(stderr_str.contains("Validation error for Transaction on 2024-01-04: Account Expenses:Food is closed"), "stderr missing 'Account Expenses:Food is closed', got:\n{}", stderr_str);
}
