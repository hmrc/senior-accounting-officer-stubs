package uk.gov.hmrc.senioraccountingofficerstubs.models

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

case class SeniorAccountingOfficer (
    name: String,
    startDate: String,
    endDate: String,
    email: String
                                   )

object SeniorAccountingOfficer {
  implicit val CompanyFormat: OFormat[SeniorAccountingOfficer] = Json.format[SeniorAccountingOfficer]
}
