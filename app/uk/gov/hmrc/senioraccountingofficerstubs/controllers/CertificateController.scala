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
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficerstubs.models.CertificateResponse

import javax.inject.Inject

class CertificateController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val stubbedSaoSubscriptionId   = "123"
  private val stubbedCertificateResponse = CertificateResponse(
    "NOT0123456789",
    "2026-03-01T12:00:14Z"
  )

  def postCertificate(saoSubscriptionId: String): Action[String] = Action(parse.tolerantText) { implicit request =>
    JsonErrorHandling.parseJson(request.body) match {
      case Right(json) =>
        val errors = JsonErrorHandling.Validators.validateCertificate(json)
        if errors.nonEmpty then JsonErrorHandling.badRequest(errors)
        else if saoSubscriptionId == stubbedSaoSubscriptionId then Ok(Json.toJson(stubbedCertificateResponse))
        else NotFound
      case Left(errorResult) =>
        errorResult
    }
  }
}
