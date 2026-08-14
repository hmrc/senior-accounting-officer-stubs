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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers.dpswrite

import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.OpenApiAction
import uk.gov.hmrc.senioraccountingofficerstubs.models.CertificateResponse
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.PostSignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.OpenApiSchema
import uk.gov.hmrc.senioraccountingofficerstubs.utils.OpenApiValidator.AllowListRules
import uk.gov.hmrc.senioraccountingofficerstubs.utils.ValidationErrorFormatter.toStandardHipFailures

import scala.concurrent.ExecutionContext
import scala.util.Random

import javax.inject.Inject

class PostCertificateController @Inject() (
    cc: ControllerComponents,
    openApiAction: OpenApiAction,
    repository: PostSignupConfigRepository
)(using
    ExecutionContext
) extends BackendController(cc)
    with Logging {

  private def generateCertificateId = {
    val num = Random.nextInt(10000000)
    "CRT" + f"$num%010d"
  }

  def postCertificate(saoSubscriptionId: String): Action[String] = {
    openApiAction[JsValue](logger)(
      OpenApiSchema.DpsWriteApi,
      allowList = List(AllowListRules.ignoreEmailValidationRule)
    )
      .fold(report => report.toStandardHipFailures) { implicit request =>
        repository.get(saoSubscriptionId).map {
          case Some(config) =>
            val status: Int  = config.postCertificate.map(_.status).fold(201)(identity)
            val body: String = config.postCertificate
              .flatMap(_.defaultBodyOverride)
              .fold(
                Json.toJson(CertificateResponse(generateCertificateId)).toString
              )(identity)
            Status(status)(body).as(JSON)
          case _ =>
            Created(Json.toJson(CertificateResponse(generateCertificateId)))
        }
      }
  }

}
