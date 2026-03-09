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

import play.api.libs.json.*
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.models.NotificationRequest

import javax.inject.Inject

class NotificationController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val stubbedSaoSubscriptionId   = "123"
  private val stubbedNotificationPayload = Json.obj(
    "id"        -> "NOT0123456789",
    "timestamp" -> "2026-03-01T12:00:14Z"
  )

  def postNotification(saoSubscriptionId: String): Action[JsValue] = Action(parse.json) { implicit request =>
    request.body.validate[NotificationRequest] match {
      case JsSuccess(_, _) =>
        if saoSubscriptionId == stubbedSaoSubscriptionId then {
          Ok(stubbedNotificationPayload)
        } else {
          NotFound
        }
      case JsError(e) => BadRequest(e.mkString)
    }
  }
}
