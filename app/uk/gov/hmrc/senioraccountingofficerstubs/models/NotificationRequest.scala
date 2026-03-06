package uk.gov.hmrc.senioraccountingofficerstubs.models

import play.api.libs.json.{Json, OFormat}

case class NotificationRequest (
    companies: List[Company],
    additionalInformation: Option[String]
                                     )

object NotificationRequest {
  implicit val NotificationRequestFormat: OFormat[NotificationRequest] = Json.format[NotificationRequest]
}

