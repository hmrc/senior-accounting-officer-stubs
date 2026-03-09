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
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.models.*

import javax.inject.Inject

class ContactDetailsController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val stubbedSaoSubscriptionId = "123"
  private val saoSubscriptionIdRegex = "^[0-9]{1,15}$".r

  private val stubbedContactDetailsPayload = ContactDetails(
    saoSubscriptionId = stubbedSaoSubscriptionId,
    name = "Jane Doe",
    email = "jane.doe@acme.example"
  )

  private def validateId(id: String): Option[Result] = id match {
    case saoSubscriptionIdRegex() => None
    case _ => Some(BadRequest(Json.obj("error" -> "Invalid subscription ID format")))
  }

  private def checkIdExists(id: String): Option[Result] =
    if (id == stubbedSaoSubscriptionId) None
    else Some(NotFound(Json.obj("error" -> "Subscription ID not found")))

  def getContactDetails(saoSubscriptionId: String): Action[AnyContent] = Action {
    validateId(saoSubscriptionId)
      .orElse(checkIdExists(saoSubscriptionId))
      .getOrElse(Ok(Json.toJson(stubbedContactDetailsPayload)))
  }

  def putContactDetails(saoSubscriptionId: String): Action[JsValue] = Action(parse.json) { implicit request =>
    validateId(saoSubscriptionId)
      .orElse {
        request.body.validate[ContactDetailsRequest] match {
          case JsSuccess(_, _) => checkIdExists(saoSubscriptionId)
          case JsError(errors) => Some(BadRequest(JsError.toJson(errors)))
        }
      }
      .getOrElse(NoContent)
  }
}



