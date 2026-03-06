package uk.gov.hmrc.senioraccountingofficerstubs.models

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

case class SeniorAccountingOfficer (
    name: String,
    startDate: LocalDate,
    endDate: LocalDate,
    email: String
                                   )

object SeniorAccountingOfficer {
  implicit val CompanyFormat: OFormat[SeniorAccountingOfficer] = Json.format[SeniorAccountingOfficer]
}
