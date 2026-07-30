/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.senioraccountingofficerstubs.utils

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request.Method
import com.atlassian.oai.validator.model.{Response, SimpleRequest, SimpleResponse}
import com.atlassian.oai.validator.report.ValidationReport.Level.*
import com.atlassian.oai.validator.report.{LevelResolver, ValidationReport}
import org.apache.pekko.stream.Materializer
import play.api.libs.json.{Json, Reads}
import play.api.mvc.{Request, RequestHeader, Result}
import uk.gov.hmrc.senioraccountingofficerstubs.models.ApiError
import uk.gov.hmrc.senioraccountingofficerstubs.models.hip.{Failure, Failures, StandardHipFailures}

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

enum OpenApiSchema(val resourcePath: String) {
  case SaoDigitalApi extends OpenApiSchema("schemas/openapi/SAO Digital API v1.0.9.yaml")
}

object OpenApiValidator {

  def of(openApi: OpenApiSchema)(pathPrefix: String): EndpointValidator = {
    val openApiAsString                        = Source.fromResource(openApi.resourcePath).mkString
    val validator: OpenApiInteractionValidator = OpenApiInteractionValidator
      .createForInlineApiSpecification(openApiAsString)
      .withBasePathOverride(pathPrefix)
      .withLevelResolver(
        LevelResolver
          .create()
          .withLevel("validation.request.path.missing", ERROR)
          .build()
      )
      .build()
    new EndpointValidator(validator)
  }

}

final class EndpointValidator private[utils] (validator: OpenApiInteractionValidator) {

  extension (request: RequestHeader) {
    private def getMethod: Method = Method.valueOf(request.method.toUpperCase)
  }

  def validateRequest(request: Request[String]): Either[ValidationReport, String] =
    validator
      .validateRequest(buildRequest(request)) match {
      case report if report.hasErrors => Left(report)
      case _                          => Right(request.body)
    }

  def validateRequestAs[T](request: Request[String])(using reads: Reads[T]): Either[ValidationReport, T] =
    validator
      .validateRequest(buildRequest(request)) match {
      case report if report.hasErrors => Left(report)
      case _                          => Right(Json.parse(request.body).as[T])
    }

  private def buildRequest(request: Request[String]): SimpleRequest = {
    val baseBuilder =
      (request.getMethod match {
        case Method.GET  => SimpleRequest.Builder.get
        case Method.POST => SimpleRequest.Builder.post
        case Method.PUT  => SimpleRequest.Builder.put
        case _           => ???
      })(request.uri)

    val builderWithHeaders = request.headers.toMap.toSeq
      .foldLeft(
        baseBuilder
      ) { case (builder, (header, value)) =>
        builder.withHeader(header, value*)
      }

    val buildWithBody = builderWithHeaders.withBody(request.body)

    buildWithBody.build()
  }

  def validateResponse(result: Result)(using request: Request[String], mat: Materializer): ValidationReport =
    validator
      .validateResponse(request.uri, request.getMethod, buildResponse(result))

  private def buildResponse(result: Result)(using Materializer): Response = {
    val baseBuilder = SimpleResponse.Builder.status(result.header.status)

    val builderWithHeaders = result.header.headers.toSeq
      .foldLeft(
        baseBuilder
      ) { case (builder, (header, value)) =>
        builder.withHeader(header, value)
      }

    val bodyAsString = Await.result(result.body.consumeData, 5.seconds).decodeString("utf-8")
    builderWithHeaders.withBody(bodyAsString).build()
  }

}

object ValidationErrorFormatter {

  extension (message: ValidationReport.Message) {
    private def fieldPath: Option[String] =
      for {
        context <- message.getContext.toScala
        pointer <- context.getPointers.toScala
      } yield pointer.getInstance

    private def param: Option[String] =
      for {
        context <- message.getContext.toScala
        param   <- context.getParameter.toScala
      } yield param.getName
  }

  extension (report: ValidationReport) {

    def toStandardHipFailures: StandardHipFailures = {
      val failures: Seq[Failure] = report.getMessages.asScala.map { message =>
        message.getKey match {
          case key if key.contains(".parameter") =>
            Failure(
              `type` = message.getKey,
              reason = message.param.fold(message.toString)(path => s"$path ${message.getMessage}")
            )
          case key if key.contains(".body") =>
            Failure(
              `type` = message.getKey,
              reason = message.fieldPath.fold(message.toString)(path => s"$path ${message.getMessage}")
            )
        }
      }.toSeq
      StandardHipFailures(
        origin = "HIP",
        response = Failures(failures = failures)
      )
    }

    def toApiError: Seq[ApiError] = report.getMessages.asScala.map { message =>
      // Extract the field path using the built-in instance location context
      val fieldPath: Option[String] =
        for {
          context <- message.getContext.toScala
          pointer <- context.getPointers.toScala
        } yield pointer.getInstance

      ApiError(
        fieldPath,
        message.toString
      )
    }.toSeq
  }

}
