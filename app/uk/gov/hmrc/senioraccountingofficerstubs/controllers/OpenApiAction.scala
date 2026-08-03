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
import uk.gov.hmrc.senioraccountingofficerstubs.models.CorrelatableRequest
import uk.gov.hmrc.senioraccountingofficerstubs.utils.ValidationErrorFormatter.toStandardHipFailures
import uk.gov.hmrc.senioraccountingofficerstubs.utils.{EndpointValidator, OpenApiSchema, OpenApiValidator}

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

trait OpenApiActionEmptyInterface {
  def fold[L](
      badRequest: ValidationReport => L
  )(
      block: CorrelatableRequest[AnyContentAsEmpty.type] => Future[Result]
  )(using Writes[L]): Action[AnyContentAsEmpty.type]
}

trait OpenApiActionInterface[R] {

  def fold[L](
      badRequest: ValidationReport => L
  )(
      block: CorrelatableRequest[R] => Future[Result]
  )(using Writes[L], Reads[R]): Action[String]

}

class OpenApiAction @Inject() (controllerComponents: ControllerComponents)(using ExecutionContext, Materializer) {

  def apply[T](logger: Logger)(openApi: OpenApiSchema): OpenApiActionInterface[T] =
    OpenApiActionImpl[T](logger)(openApi)

  def ignoreBody(logger: Logger)(openApi: OpenApiSchema): OpenApiActionEmptyInterface =
    OpenApiActionImpl(logger)(openApi)

  private class OpenApiActionImpl[R](logger: Logger)(openApi: OpenApiSchema)
      extends OpenApiActionInterface[R]
      with OpenApiActionEmptyInterface {

    override def fold[L](
        badRequest: ValidationReport => L
    )(
        block: CorrelatableRequest[AnyContentAsEmpty.type] => Future[Result]
    )(using Writes[L]): Action[AnyContentAsEmpty.type] = {
      controllerComponents.actionBuilder(controllerComponents.parsers.ignore(AnyContentAsEmpty)).async {
        implicit request =>
          def schemaValidator         = OpenApiValidator.of(openApi)
          val requestValidationReport = schemaValidator.validateRequest(request)

          foldCore(schemaValidator, requestValidationReport)(badRequest)(block)
      }
    }

    override def fold[L](
        badRequest: ValidationReport => L
    )(
        block: CorrelatableRequest[R] => Future[Result]
    )(using Writes[L], Reads[R]): Action[String] = {
      controllerComponents.actionBuilder(controllerComponents.parsers.tolerantText).async { implicit request =>
        val schemaValidator         = OpenApiValidator.of(openApi)
        val requestValidationReport = schemaValidator.validateRequestAs[R](request)

        foldCore(schemaValidator, requestValidationReport)(badRequest)(block)
      }
    }

    private def foldCore[L, T](
        schemaValidator: EndpointValidator,
        requestValidationReport: Either[ValidationReport, T]
    )(
        badRequest: ValidationReport => L
    )(block: CorrelatableRequest[T] => Future[Result])(using RequestHeader, Writes[L]) = {
      val correlationId = summon[RequestHeader].headers.get("correlationId")

      val futureResult = requestValidationReport match {
        case Left(report) =>
          Future.successful(BadRequest(Json.toJson(badRequest(report))))
        case Right(validatedBody) =>
          val request = summon[RequestHeader].withBody(validatedBody)
          block(CorrelatableRequest(request, correlationId.fold("")(identity)))
      }

      for {
        result                   <- futureResult
        responseValidationReport <- schemaValidator.validateResponse(result)
      } yield {
        if responseValidationReport.hasErrors then {

          val errors = Json.toJson(responseValidationReport.toStandardHipFailures.response.failures).toString
          logger.warn(s"${correlationId.fold("")(cId => s"correlationId=$cId, ")}$errors")
        }
        result
      }
    }

  }

}
