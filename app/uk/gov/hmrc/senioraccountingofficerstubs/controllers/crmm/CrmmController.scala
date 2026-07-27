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
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.CrmmHelper
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficerstubs.models.ApiError
import uk.gov.hmrc.senioraccountingofficerstubs.models.crmm.*
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.SignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.generateCustomerId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import javax.inject.Inject

class CrmmController @Inject() (cc: ControllerComponents, repository: SignupConfigRepository)(using
    ExecutionContext
) extends BackendController(cc) {
  def retrieveCustomer(): Action[String] = Action(parse.tolerantText).async { implicit request =>
    CrmmHelper
      .validateHeaders(request.headers)
      .fold(
        headerError => Future.successful(BadRequest(headerError)),
        correlationId => {
          JsonErrorHandling.parseJson(request.body) match {
            case Right(json) =>
              val errors = JsonErrorHandling.Validators.validateRetrieveCustomerRequest(json)
              if errors.nonEmpty then Future.successful(JsonErrorHandling.badRequest(errors))
              else
                val retrieveCustomerRequest = json.as[RetrieveCustomerRequest]
                val neitherCrnOrUtrIncluded =
                  retrieveCustomerRequest.companyRegistrationNumber.isEmpty && retrieveCustomerRequest.uniqueTaxReference.isEmpty

                if neitherCrnOrUtrIncluded
                then
                  Future.successful(
                    JsonErrorHandling
                      .badRequest(
                        ApiError(Some("companyRegistrationNumber and uniqueTaxReference"), "MISSING_REQUIRED_FIELD")
                      )
                  )
                else
                  repository.get(correlationId).map {
                    case Some(config) =>
                      val status: Int  = config.postCrmmRetrieveCustomer.map(_.status).fold(201)(identity)
                      val body: String = config.postCrmmRetrieveCustomer
                        .flatMap(_.defaultBodyOverride)
                        .fold(
                          Json
                            .toJson(
                              RetrieveCustomerResponse(
                                customerId = Some(generateCustomerId),
                                errorDescription = None,
                                existingCustomer = false,
                                status = "Success"
                              )
                            )
                            .toString
                        )(identity)
                      Status(status)(body).as(JSON)
                    case _ =>
                      Created(
                        Json.toJson(
                          RetrieveCustomerResponse(
                            customerId = Some(generateCustomerId),
                            errorDescription = None,
                            existingCustomer = false,
                            status = "Success"
                          )
                        )
                      )
                  }
            case Left(errorResult) => Future.successful(errorResult)
          }
        }
      )
  }
}
