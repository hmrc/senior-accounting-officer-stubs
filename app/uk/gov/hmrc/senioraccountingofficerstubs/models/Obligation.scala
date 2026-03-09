package uk.gov.hmrc.senioraccountingofficerstubs.models

final case class Obligation(saoSubscriptionId: String, subscription: Subscription, submissions: Seq[Submission])
