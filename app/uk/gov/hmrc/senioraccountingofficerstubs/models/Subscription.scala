package uk.gov.hmrc.senioraccountingofficerstubs.models

final case class Subscription(
    subscriptionTimestamp: String,
    companyRegistrationNumber: String,
    uniqueTaxReference: String,
    companyName: String,
    contacts: Seq[Contact]
)
