/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.senioraccountingofficerstubs.controllers

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future

import javax.inject.Inject

class ObligationController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val stubbedSaoSubscriptionId = "123"
  private val stubbedObligationPayload = Json.obj(
    "saoSubscriptionId" -> stubbedSaoSubscriptionId,
    "subscription"      -> Json.obj(
      "subscriptionTimestamp"     -> "2021-01-01T00:00:00Z",
      "companyRegistrationNumber" -> "01234567",
      "uniqueTaxReference"        -> "1234567890",
      "companyName"               -> "Stub Global",
      "contacts"                  -> Json.arr(Json.obj("name" -> "jacob", "email" -> "example@example.com"))
    ),
    "submissions" -> Json.arr(
      Json.obj(
        "financialYearEnd" -> 2025,
        "notification"     -> Json.obj("id" -> "notificationId", "notificationTimestamp" -> "notificationTimestamp"),
        "certificate"      -> Json.obj("id" -> "certificateId", "certificateTimestamp" -> "certificateTimestamp")
      )
    )
  )

  def getObligation(saoSubscriptionId: String): Action[AnyContent] = Action.async { implicit request =>
    if saoSubscriptionId == stubbedSaoSubscriptionId then {
      Future.successful(Ok(stubbedObligationPayload))
    } else {
      Future.successful(NotFound)
    }
  }
}
