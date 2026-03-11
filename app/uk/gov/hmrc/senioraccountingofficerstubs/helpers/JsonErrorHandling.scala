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

package uk.gov.hmrc.senioraccountingofficerstubs.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.path.PathType
import com.networknt.schema.{Error, InputFormat, Schema, SchemaRegistry, SchemaRegistryConfig, SpecificationVersion}
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest

import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Try

object JsonErrorHandling {

  final case class ApiError(path: Option[String], reason: String)

  def parseJson(body: String): Either[Result, JsValue] =
    Try(Json.parse(body)).toEither.left.map(_ => malformedRequest)

  private def malformedRequest: Result =
    badRequest(Seq(ApiError(None, "MALFORMED_REQUEST")))

  def badRequest(errors: Seq[ApiError]): Result =
    BadRequest(
      JsArray(
        errors.map { error =>
          error.path match {
            case Some(path) => Json.obj("path" -> path, "reason" -> error.reason)
            case None       => Json.obj("reason" -> error.reason)
          }
        }
      )
    )

  object Validators {

    private val schemaRegistry = SchemaRegistry.withDefaultDialect(
      SpecificationVersion.DRAFT_2020_12,
      builder =>
        builder.schemaRegistryConfig(
          SchemaRegistryConfig.builder().pathType(PathType.LEGACY).formatAssertionsEnabled(true).build()
        )
    )

    def validateNotification(json: JsValue): Seq[ApiError] =
      validate(notificationSchema, json, rootPrefix = None)

    def validateSubscription(json: JsValue): Seq[ApiError] =
      validate(subscriptionSchema, json, rootPrefix = Some("body"))

    def validateContactDetails(json: JsValue): Seq[ApiError] =
      validate(contactDetailsSchema, json, rootPrefix = None)

    private def validate(schema: Schema, json: JsValue, rootPrefix: Option[String]): Seq[ApiError] =
      schema
        .validate(toJackson(json))
        .asScala
        .toSeq
        .map(toApiError(_, rootPrefix))
        .sortBy(_.path.getOrElse(""))

    private def toJackson(json: JsValue): JsonNode =
      Json.parse(json.toString()).as[JsonNode]

    private def toApiError(error: Error, rootPrefix: Option[String]): ApiError = {
      val reason = error.getKeyword match {
        case "required"             => "MISSING_REQUIRED_FIELD"
        case "type"                 => "INVALID_DATA_TYPE"
        case "additionalProperties" => "INVALID_DATA_TYPE"
        case "pattern"              => "INVALID_FORMAT"
        case "format"               => "INVALID_FORMAT"
        case "enum"                 => "INVALID_ENUM_VALUE"
        case "minItems"             => "ARRAY_MIN_ITEMS_NOT_MET"
        case "maxItems"             => "LENGTH_OUT_OF_BOUNDS"
        case "maxLength"            => "LENGTH_OUT_OF_BOUNDS"
        case "minLength" if isEmptyString(error) => "CANNOT_BE_EMPTY"
        case "minLength"            => "LENGTH_OUT_OF_BOUNDS"
        case _                      => "INVALID_DATA_TYPE"
      }
      ApiError(pathFor(error, rootPrefix), reason)
    }

    private def pathFor(error: Error, rootPrefix: Option[String]): Option[String] = {
      val basePath = applyRootPrefix(normalizePath(error.getInstanceLocation.toString), rootPrefix)
      error.getKeyword match {
        case "required" =>
          val property = Option(error.getProperty)
          Some(property.fold(basePath)(appendPath(basePath, _)))
        case "additionalProperties" =>
          val property = Option(error.getProperty)
          Some(property.fold(basePath)(appendPath(basePath, _)))
        case _ if basePath.isEmpty =>
          Some("body")
        case _ =>
          Some(basePath)
      }
    }

    private def normalizePath(path: String): String =
      path.stripPrefix("$").stripPrefix(".")

    private def applyRootPrefix(path: String, rootPrefix: Option[String]): String =
      rootPrefix match {
        case Some(prefix) if path.isEmpty => prefix
        case Some(prefix)                 => s"$prefix.$path"
        case None                        => path
      }

    private def appendPath(base: String, child: String): String =
      if base.isEmpty then s"body.$child" else s"$base.$child"

    private def isEmptyString(error: Error): Boolean =
      Option(error.getInstanceNode).exists(node => node.isTextual && node.textValue().isEmpty)

    private lazy val notificationSchema = loadSchema("schemas/notification-request-schema.yaml")
    private lazy val subscriptionSchema = loadSchema("schemas/subscription-request-schema.yaml")
    private lazy val contactDetailsSchema = loadSchema("schemas/contact-details-request-schema.yaml")

    private def loadSchema(path: String): Schema = {
      val resource = Option(getClass.getClassLoader.getResourceAsStream(path))
        .getOrElse(throw new IllegalStateException(s"Missing schema resource: $path"))
      val yaml =
        try Source.fromInputStream(resource).mkString
        finally resource.close()
      schemaRegistry.getSchema(yaml, InputFormat.YAML)
    }
  }
}
