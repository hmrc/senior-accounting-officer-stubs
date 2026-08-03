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

import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.OpenApiAction
import uk.gov.hmrc.senioraccountingofficerstubs.models.crmm.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.*
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.PostSignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.OpenApiSchema
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.generateCustomerId
import uk.gov.hmrc.senioraccountingofficerstubs.utils.ValidationErrorFormatter.toStandardHipFailures

import scala.concurrent.ExecutionContext

import javax.inject.Inject

class CrmmController @Inject() (
    cc: ControllerComponents,
    openApiAction: OpenApiAction,
    repository: PostSignupConfigRepository
)(using
    ExecutionContext
) extends BackendController(cc)
    with Logging {

  def retrieveCustomer(): Action[String] =
    openApiAction[RetrieveCustomerRequest](logger)(OpenApiSchema.CrmmApi)
      .fold(report => report.toStandardHipFailures) { implicit request =>
        val retrieveCustomerRequest = request.body

        repository
          .getByCrnAndUtr(
            retrieveCustomerRequest.companyRegistrationNumber,
            retrieveCustomerRequest.uniqueTaxReference
          )
          .map {
            case Some(config) => retrieveConfiguredResponse(config)
            case _            => Ok(Json.toJson(generateStandardResponse))
          }
      }

  private def retrieveConfiguredResponse(config: PostSignupStubConfiguration): Result = {
    val status: Int = config.getSubscriptionAndPostRetrieveCustomerId
      .collect { case PostRetrieveCustomerIdConfig(_, status, _) =>
        status
      }
      .fold(200)(identity)

    val body: String = config.getSubscriptionAndPostRetrieveCustomerId
      .collect { case PostRetrieveCustomerIdConfig(getSubscription, status, Some(defaultBodyOverride)) =>
        defaultBodyOverride
      }
      .fold(Json.toJson(generateStandardResponse).toString)(identity)

    Status(status)(body).as(JSON)
  }

  private def generateStandardResponse: RetrieveCustomerResponse = {
    RetrieveCustomerResponse(
      customerId = Some(generateCustomerId),
      errorDescription = None,
      existingCustomer = true,
      status = "Success"
    )
  }
}
