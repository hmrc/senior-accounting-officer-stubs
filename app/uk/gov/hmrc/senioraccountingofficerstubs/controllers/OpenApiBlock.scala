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

import com.atlassian.oai.validator.report.ValidationReport
import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.Results.BadRequest
import play.api.mvc.*
import uk.gov.hmrc.senioraccountingofficerstubs.utils.{OpenApiSchema, OpenApiValidator}

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class OpenApiBlock @Inject() (controllerComponents: ControllerComponents)(using ExecutionContext, Materializer) {

  def apply[T](logger: Logger)(openApi: OpenApiSchema, pathPrefix: String): _OpenApiBlock[T] =
    _OpenApiBlock[T](openApi: OpenApiSchema, pathPrefix: String, logger)

  class _OpenApiBlock[R](openApi: OpenApiSchema, pathPrefix: String, logger: Logger) {
    def fold[L](badRequest: ValidationReport => L)(
        block: Request[R] => Future[Result]
    )(using Writes[L], Reads[R]): Action[String] =
      controllerComponents.actionBuilder(controllerComponents.parsers.tolerantText).async { implicit request =>
        val schemaValidator         = OpenApiValidator.of(openApi)(pathPrefix)
        val requestValidationReport = schemaValidator.validateRequestAs[R](request)

        val futureResult = requestValidationReport.left
          .map(report => Future.successful(BadRequest(Json.toJson(badRequest(report)))))
          .map(validatedBody => block(summon[RequestHeader].withBody(validatedBody)))
          .merge

        for {
          result <- futureResult
        } yield {
          val responseValidationReport = schemaValidator.validateResponse(result)
          if responseValidationReport.hasErrors then logger.warn(responseValidationReport.toString)
          result
        }
      }
  }

}
