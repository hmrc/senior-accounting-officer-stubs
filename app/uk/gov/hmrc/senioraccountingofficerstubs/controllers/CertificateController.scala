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

import play.api.libs.json.*
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.JsonErrorHandling.subscriptionIdLengthError
import uk.gov.hmrc.senioraccountingofficerstubs.models.CertificateResponse
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.PostSignupConfigRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

import javax.inject.Inject

class CertificateController @Inject() (cc: ControllerComponents, repository: PostSignupConfigRepository)(using
    ExecutionContext
) extends BackendController(cc) {

  private def generateCertificateId = {
    val num = Random.nextInt(10000000)
    "CRT" + f"$num%010d"
  }

  def postCertificate(saoSubscriptionId: String): Action[String] = Action(parse.tolerantText).async {
    implicit request =>
      JsonErrorHandling.parseJson(request.body) match {
        case Right(json) =>
          val jsonErrors = JsonErrorHandling.Validators.validateCertificate(json)
          val errors     = if saoSubscriptionId.size > 15 then {
            subscriptionIdLengthError +: jsonErrors
          } else {
            jsonErrors
          }
          if errors.nonEmpty then Future.successful(JsonErrorHandling.badRequest(errors))
          else
            repository.get(saoSubscriptionId).map {
              case Some(config) =>
                val status: Int  = config.postCertificate.map(_.status).fold(201)(identity)
                val body: String = config.postCertificate
                  .flatMap(_.defaultBodyOverride)
                  .fold(
                    Json.toJson(CertificateResponse(generateCertificateId)).toString
                  )(identity)
                Status(status)(body).as(JSON)
              case _ =>
                Created(Json.toJson(CertificateResponse(generateCertificateId)))
            }
        case Left(errorResult) => Future.successful(errorResult)
      }
  }
}
