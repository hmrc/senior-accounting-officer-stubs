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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers.digital

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.connectors.FileUploadSdesStubConnector
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.digital.SdesNotificationController.*

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class SdesNotificationController @Inject() (
    cc: ControllerComponents,
    fileUploadSdesStubConnector: FileUploadSdesStubConnector
)(using ExecutionContext)
    extends BackendController(cc) {

  def fileReady(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.headers.get("X-Client-ID") match {
      case Some(`supportedClientId`) =>
        (request.body \ "informationType").asOpt[String] match {
          case Some(`supportedInformationType`) =>

            fileUploadSdesStubConnector.notifyFileReady(request.body).map { response =>
              Status(response.status)(response.body)
            }

          case _ =>
            Future.successful(
              BadRequest(
                Json.obj(
                  "code"    -> "INVALID_INFORMATION_TYPE",
                  "message" -> "The supplied informationType is not supported"
                )
              )
            )
        }

      case _ =>
        Future.successful(
          Forbidden(
            Json.obj(
              "code"    -> "INVALID_CLIENT_ID",
              "message" -> "The supplied X-Client-ID is not supported"
            )
          )
        )
    }
  }
}

object SdesNotificationController {
  private val supportedClientId        = "senior-accounting-officer"
  private val supportedInformationType = "DSAO"
}
