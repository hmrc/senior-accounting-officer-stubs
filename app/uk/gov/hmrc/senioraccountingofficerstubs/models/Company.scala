package uk.gov.hmrc.senioraccountingofficerstubs.models

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

case class Company (
                     companyName: String,
                     uniqueTaxReference: String,
                     companyReferenceNumber: String,
                     companyType: String,
                     financialYearEndDate: String,
                     seniorAccountingOfficers: List[SeniorAccountingOfficer]
                   )

object Company {
  implicit val CompanyFormat: OFormat[Company] = Json.format[Company]
}

