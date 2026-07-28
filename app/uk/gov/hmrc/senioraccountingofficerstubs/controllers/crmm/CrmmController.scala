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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers.crmm

import play.api.libs.json.*
import play.api.mvc.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.crmm.CrmmController.*
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficerstubs.models.ApiError
import uk.gov.hmrc.senioraccountingofficerstubs.models.crmm.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.PostSignupStubConfiguration
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.PostSignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.generateCustomerId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import javax.inject.Inject

class CrmmController @Inject() (cc: ControllerComponents, repository: PostSignupConfigRepository)(using
    ExecutionContext
) extends BackendController(cc) {
  def retrieveCustomer(): Action[String] = Action(parse.tolerantText).async { implicit request =>
    validateHeaders(request.headers)
      .fold(
        Future.successful(_),
        correlationId => {
          JsonErrorHandling
            .parseJson(request.body)
            .fold(
              Future.successful(_),
              json =>
                JsonErrorHandling.Validators.validateRetrieveCustomerRequest(json) match {
                  case errors if errors.nonEmpty => Future.successful(JsonErrorHandling.badRequest(errors))
                  case _                         =>
                    validateAtLeastOneId(json) match {
                      case Left(error) => Future.successful(error)
                      case _           =>
                        repository.get(correlationId).map {
                          case Some(config) => retrieveConfiguredResponse(config)
                          case _            => Ok(Json.toJson(generateStandardResponse))
                        }
                    }
                }
            )
        }
      )
  }

  def validateHeaders(requestHeaders: Headers): Either[Result, String] = {
    for {
      _ <- requestHeaders
        .get(sourceSysRefHeader)
        .filter(_.nonEmpty)
        .toRight(
          JsonErrorHandling
            .badRequest(
              ApiError(Some(s"headers.$sourceSysRefHeader"), "MISSING_REQUIRED_FIELD")
            )
        )
      correlationId <- requestHeaders
        .get(correlationIdHeader)
        .filter(_.nonEmpty)
        .toRight(
          JsonErrorHandling
            .badRequest(
              ApiError(Some(s"headers.$correlationIdHeader"), "MISSING_REQUIRED_FIELD")
            )
        )
    } yield correlationId
  }

  def validateAtLeastOneId(json: JsValue): Either[Result, RetrieveCustomerRequest] = {
    json.as[RetrieveCustomerRequest] match {
      case RetrieveCustomerRequest(None, None) =>
        Left(
          JsonErrorHandling
            .badRequest(
              ApiError(Some("companyRegistrationNumber or uniqueTaxReference"), "MISSING_REQUIRED_FIELD")
            )
        )
      case request => Right(request)
    }
  }

  def retrieveConfiguredResponse(config: PostSignupStubConfiguration): Result = {
    val status: Int  = config.postCrmmRetrieveCustomer.map(_.status).fold(200)(identity)
    val body: String = config.postCrmmRetrieveCustomer
      .flatMap(_.defaultBodyOverride)
      .fold(
        Json.toJson(generateStandardResponse).toString
      )(identity)
    Status(status)(body).as(JSON)
  }

  def generateStandardResponse: RetrieveCustomerResponse = {
    RetrieveCustomerResponse(
      customerId = Some(generateCustomerId),
      errorDescription = None,
      existingCustomer = true,
      status = "Success"
    )
  }
}

object CrmmController {
  val correlationIdHeader = "correlationId"
  val sourceSysRefHeader  = "sourceSysRef"
}
