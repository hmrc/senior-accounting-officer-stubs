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
import play.api.mvc.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.models.*

import javax.inject.Inject

class ContactDetailsController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val stubbedSaoSubscriptionId = "123"
  private val saoSubscriptionIdRegex   = "^[0-9]{1,15}$".r

  private val stubbedContactDetailsPayload = ContactDetails(
    name = "Jane Doe",
    email = "jane.doe@acme.example"
  )

  private def validateIdFormat(id: String): Option[Result] = id match {
    case saoSubscriptionIdRegex() => None
    case _                        => Some(BadRequest(Json.obj("error" -> "Invalid subscription ID format")))
  }

  private def validateIdExists(id: String): Option[Result] = {
    if id == stubbedSaoSubscriptionId then None
    else Some(NotFound(Json.obj("error" -> "SubscriptionId not found")))
  }

  private def validateBody(body: JsValue): Option[Result] = {
    body.validate[ContactDetailsRequest] match {
      case JsSuccess(_, _) => None
      case JsError(errors) => Some(BadRequest(JsError.toJson(errors)))
    }
  }

  def getContactDetails(saoSubscriptionId: String): Action[AnyContent] = Action {
    validateIdFormat(saoSubscriptionId)
      .orElse(validateIdExists(saoSubscriptionId))
      .getOrElse(Ok(Json.toJson(stubbedContactDetailsPayload)))
  }

  def putContactDetails(saoSubscriptionId: String): Action[JsValue] = Action(parse.json) { implicit request =>
    validateIdFormat(saoSubscriptionId)
      .orElse(validateBody(request.body))
      .orElse(validateIdExists(saoSubscriptionId))
      .getOrElse(NoContent)
  }
}
