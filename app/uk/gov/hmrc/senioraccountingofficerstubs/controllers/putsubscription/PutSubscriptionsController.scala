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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers.putsubscription

import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.OpenApiAction
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.putsubscription.PutSubscriptionsController.subscriptionIdLengthError
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficerstubs.models.ApiError
import uk.gov.hmrc.senioraccountingofficerstubs.models.putsubscription.Subscription
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.SignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.ValidationErrorFormatter.*
import uk.gov.hmrc.senioraccountingofficerstubs.utils.{OpenApiSchema, OpenApiValidator}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import javax.inject.Inject

class PutSubscriptionsController @Inject() (
    cc: ControllerComponents,
    openApiAction: OpenApiAction,
    repository: SignupConfigRepository
)(using
    ExecutionContext,
    Materializer
) extends BackendController(cc)
    with Logging {

  def putSubscription0(saoSubscriptionId: String): Action[String] = Action(parse.tolerantText).async {
    implicit request =>
      JsonErrorHandling.parseJson(request.body) match {
        case Right(json) =>
          val jsonErrors = JsonErrorHandling.Validators.validateSubscription(json)
          val errors     = if saoSubscriptionId.length > 15 then {
            subscriptionIdLengthError +: jsonErrors
          } else { jsonErrors }
          if errors.nonEmpty
          then Future.successful(JsonErrorHandling.badRequest(errors))
          else
            val subscription = json.as[Subscription]
            repository.get(subscription.nominatedCompany.utr).map {
              case Some(config) =>
                config.putDpsSubscription
                  .fold(Created)(config =>
                    Status(config.status)(config.defaultBodyOverride.fold("")(identity)).as(JSON)
                  )
              case _ => Created
            }
        case Left(errorResult) =>
          Future.successful(errorResult)
      }
  }

  def putSubscription_1(saoSubscriptionId: String): Action[String] = Action(parse.tolerantText).async {
    implicit request =>
      val schemaValidator = OpenApiValidator
        .of(OpenApiSchema.DpsWriteApi)
      val requestValidationReport = schemaValidator
        .validateRequestAs[Subscription](request)

      requestValidationReport.left
        .map(report => Future.successful(BadRequest(Json.toJson(report.toStandardHipFailures))))
        .map { subscription =>
          val response: Future[Result] = repository.get(subscription.nominatedCompany.utr).map {
            case Some(config) =>
              config.putDpsSubscription
                .fold(Created)(config => Status(config.status)(config.defaultBodyOverride.fold("")(identity)).as(JSON))
            case _ => Created
          }

          for {
            result                   <- response
            responseValidationReport <- schemaValidator.validateResponse(result)

          } yield {
            if responseValidationReport.hasErrors then logger.warn(responseValidationReport.toString)
            result
          }
        }
        .merge
  }

  def putSubscription(saoSubscriptionId: String): Action[String] =
    openApiAction[Subscription](logger)(OpenApiSchema.DpsWriteApi)
      .fold(report => report.toStandardHipFailures) { implicit request =>
        val subscription = request.body
        repository.get(subscription.nominatedCompany.utr).map {
          case Some(config) =>
            config.putDpsSubscription
              .fold(Created)(config => Status(config.status)(config.defaultBodyOverride.fold("")(identity)).as(JSON))
          case _ => Created
        }
      }

}

object PutSubscriptionsController {
  val subscriptionIdLengthError: ApiError = ApiError(Some("subscriptionId"), "LENGTH_OUT_OF_BOUNDS")
}
