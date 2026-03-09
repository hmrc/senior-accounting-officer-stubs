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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.models.*

import javax.inject.Inject

class ContactDetailsController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val stubbedSaoSubscriptionId = "123"
  private val saoSubscriptionIdRegex   = "^[0-9]{1,15}$".r

  private val stubbedContactDetailsPayload = ContactDetails(
    saoSubscriptionId = stubbedSaoSubscriptionId,
    name = "Jane Doe",
    email = "jane.doe@acme.example"
  )

  def getContactDetails(saoSubscriptionId: String): Action[AnyContent] = Action { implicit request =>
    saoSubscriptionId match {
      case saoSubscriptionIdRegex() =>
        if saoSubscriptionId == stubbedSaoSubscriptionId then {
          Ok(Json.toJson(stubbedContactDetailsPayload))
        } else {
          NotFound
        }
      case _ =>
        BadRequest
    }
  }

  def putContactDetails(saoSubscriptionId: String): Action[JsValue] = Action(parse.json) { implicit request =>
    saoSubscriptionId match {
      case saoSubscriptionIdRegex() =>
        request.body.validate[ContactDetailsRequest] match {
          case JsSuccess(_, _) =>
            if saoSubscriptionId == stubbedSaoSubscriptionId then {
              NoContent
            } else {
              NotFound
            }
          case JsError(e) =>
            BadRequest(JsError.toJson(e))
        }
      case _ =>
        BadRequest
    }
  }

}
