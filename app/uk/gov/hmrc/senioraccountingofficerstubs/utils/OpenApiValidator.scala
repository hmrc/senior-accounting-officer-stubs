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
import com.atlassian.oai.validator.report.ValidationReport.MessageContext.Pointers
import com.atlassian.oai.validator.report.{LevelResolver, ValidationReport}
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist
import com.atlassian.oai.validator.whitelist.rule.WhitelistRule
import org.apache.pekko.stream.Materializer
import play.api.libs.json.{Json, Reads}
import play.api.mvc.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.hip.{Failure, Failures, StandardHipFailures}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

enum OpenApiSchema(val resourcePath: String, val pathPrefix: String) {
  case EtmpApi
      extends OpenApiSchema(
        resourcePath = "schemas/openapi/DSAO Subscription v1.0.0.yaml",
        pathPrefix = "/etmp"
      )
  case DpsWriteApi
      extends OpenApiSchema(
        resourcePath = "schemas/openapi/SAO Digital API v1.0.9.yaml",
        pathPrefix = "/dapm"
      )
  case DpsReadApi
      extends OpenApiSchema(
        resourcePath = "schemas/openapi/Senior Accounting Office v1.0.2.yaml",
        pathPrefix = "/business-tax/corporate-tax"
      )
  case CrmmApi
      extends OpenApiSchema(
        resourcePath = "schemas/openapi/CRMM customer details v1.0.0-a.yaml",
        pathPrefix = "/compliance/civil-investigation-and-avoidance"
      )
}

object OpenApiValidator {

  object AllowListRules {
    final case class AllowListRule(description: String, rule: WhitelistRule)

    def ignoreEmailValidationRule: AllowListRule =
      AllowListRule(
        "Ignore RFC 5321 Mailbox formatting errors",
        (msg, _, _, _) => msg.getMessage.contains("must be a valid RFC 5321 Mailbox")
      )

  }

  extension (rules: List[AllowListRules.AllowListRule]) {
    private def toValidationErrorsWhitelist: ValidationErrorsWhitelist =
      rules.foldLeft(
        ValidationErrorsWhitelist
          .create()
      )((builder, rule) =>
        builder.withRule(
          rule.description,
          rule.rule
        )
      )
  }

  def of(openApi: OpenApiSchema, allowList: List[AllowListRules.AllowListRule]): EndpointValidator = {
    val openApiAsString                        = Source.fromResource(openApi.resourcePath).mkString
    val validator: OpenApiInteractionValidator = OpenApiInteractionValidator
      .createForInlineApiSpecification(openApiAsString)
      .withBasePathOverride(openApi.pathPrefix)
      .withLevelResolver(
        LevelResolver
          .create()
          .withLevel("validation.request.path.missing", ERROR)
          .withLevel("validation.schema.invalidJson", ERROR)
          .withLevel("validation.response.contentType.invalid", ERROR)
          .withLevel("validation.response.contentType.notAllowed", ERROR)
          .withLevel("validation.response.header.missing", ERROR)
          .build()
      )
      .withWhitelist(allowList.toValidationErrorsWhitelist)
      .build()
    new EndpointValidator(validator)
  }

}

final class EndpointValidator private[utils] (val validator: OpenApiInteractionValidator) {

  extension (request: RequestHeader) {
    private def getMethod: Method = Method.valueOf(request.method.toUpperCase)
  }

  def validateRequest(request: Request[AnyContentAsEmpty.type]): Either[ValidationReport, AnyContentAsEmpty.type] =
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

  private def buildRequest(request: Request[String | AnyContentAsEmpty.type]): SimpleRequest = {
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

    val buildWithBody = request.body match {
      case body: String => builderWithHeaders.withBody(body)
      case _            => builderWithHeaders
    }

    buildWithBody.build()
  }

  def validateResponse(
      result: Result
  )(using request: RequestHeader, ec: ExecutionContext, mat: Materializer): Future[ValidationReport] = {
    for {
      response <- buildResponse(result)
    } yield validator.validateResponse(request.uri, request.getMethod, response)
  }

  private def buildResponse(result: Result)(using ExecutionContext, Materializer): Future[Response] = {
    val baseBuilder = SimpleResponse.Builder.status(result.header.status)

    val builderWithHeaders = result.header.headers.toSeq
      .foldLeft(
        baseBuilder
      ) { case (builder, (header, value)) =>
        builder.withHeader(header, value)
      }

    for {
      data <- result.body.consumeData
    } yield builderWithHeaders
      // the contentType must match exactly as the ones specified in the open api spec
      // in order for validation to take place
      .withContentType("application/json;charset=UTF-8;subtype=denodo-8.0")
      .withContentType("application/json;charset=UTF-8")
      .withBody(data.decodeString("utf-8"))
      .build()
  }
}

object ValidationErrorFormatter {

  extension (message: ValidationReport.Message) {
    private def pointers: Option[Pointers] =
      for {
        context <- message.getContext.toScala
        pointer <- context.getPointers.toScala
      } yield pointer

    private def fieldPath: Option[String] =
      message.pointers.map(_.getInstance)

    private def schema: Option[String] =
      message.pointers.map(_.getSchema)

    private def param: Option[String] =
      for {
        context <- message.getContext.toScala
        param   <- context.getParameter.toScala
      } yield param.getName

    private def formattedMessage: String = {
      val sb = StringBuilder()
      message.param.foreach { s =>
        sb.append(s.trim)
        sb.append(" ")
      }
      message.schema.foreach { s =>
        sb.append(s.trim)
        sb.append(" ")
      }
      message.fieldPath.foreach { s =>
        sb.append(s.trim)
        sb.append(" ")
      }

      sb.append(message.toString.trim)
      sb.toString
    }
  }

  extension (report: ValidationReport) {

    def toStandardHipFailures: StandardHipFailures = {
      val failures: Seq[Failure] = report.getMessages.asScala.map { message =>
        Failure(
          `type` = message.getKey,
          reason = message.formattedMessage
        )
      }.toSeq

      StandardHipFailures(
        origin = "HIP",
        response = Failures(failures = failures)
      )
    }
  }

}
