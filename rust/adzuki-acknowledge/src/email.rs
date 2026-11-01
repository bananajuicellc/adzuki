use rustledger_core::Amount;


fn render_amount(amount: &Amount) -> String {
        if amount.currency == "USD" {
            format!("${}", amount.number)
        } else {
            format!("{} {}", amount.number, amount.currency)
        }
}

pub fn render_email(acknowledgement: &crate::Acknowledgement) -> String {
    let mut out = format!("Greetings {},\n\n", acknowledgement.name);
    out.push_str("This letter gratefully acknowledges receipt of your ");
    for (index, donation) in acknowledgement.donations.iter().enumerate() {
        out.push_str(donation.date.strftime("%B %e, %Y").to_string().as_ref());
        out.push_str(" donation of ");
        out.push_str(&render_amount(&donation.amount.units));
        out.push_str(" (");

        if let Some(cost) = &donation.amount.cost {
            out.push_str(&render_amount(&Amount { number:cost.number.clone(), currency: cost.currency.clone() }));
            out.push_str(", ");
        }

        out.push_str(&donation.narration);
        out.push_str(") ");
        if index != acknowledgement.donations.len() - 1 {
            out.push_str("and ");
        }
    }

    out.push_str(" in support of Nortside Friends Meeting, Religious Society of Friends. No goods or services were provided in return for this contribution.\n\n");
    out.push_str("Timothy Sweña\n");
    out.push_str("Treasurer\n");
    out.push_str("Northside Friends Meeting\n");
    out.push_str("1020 W Bryn Mawr Ave\n");
    out.push_str("Chicago, IL 60660\n");
    out
}
