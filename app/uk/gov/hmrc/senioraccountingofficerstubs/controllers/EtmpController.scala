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

import play.api.Logging
import play.api.http.Status.*
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.NoneDefaultApiConfiguration
import uk.gov.hmrc.senioraccountingofficerstubs.models.{EtmpSuccessResponse, Success as EtmpSuccess}
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.SignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.OpenApiSchema
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.generateDsaoIdNumber
import uk.gov.hmrc.senioraccountingofficerstubs.utils.ValidationErrorFormatter.*

import scala.concurrent.ExecutionContext

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class EtmpController @Inject() (
    cc: ControllerComponents,
    openApiAction: OpenApiAction,
    repository: SignupConfigRepository
)(using ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def createEtmp: Action[String] =
    openApiAction[JsValue](logger)(OpenApiSchema.EtmpApi)
      .fold(report => report.toStandardHipFailures) { implicit request =>
        val json = request.body

        repository.get((json \ "idNumber").as[String]).map { config =>
          val response = config.flatMap(_.postEtmpSubscription) match {
            case Some(NoneDefaultApiConfiguration(NO_CONTENT, _)) => NoContent
            case Some(NoneDefaultApiConfiguration(status, body))  =>
              Status(status)(body.fold("")(identity)).as(JSON)
            case None =>
              Created(
                Json.toJson(
                  EtmpSuccessResponse(
                    EtmpSuccess(
                      Instant.now().truncatedTo(ChronoUnit.SECONDS).toString,
                      generateDsaoIdNumber
                    )
                  )
                )
              )
          }
          response.withHeaders("correlationId" -> request.correlationId)
        }
      }
}
