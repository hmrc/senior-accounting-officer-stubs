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

import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.mvc.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.GetSubscriptionController.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.getsubscription.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.*
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.PostSignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.*

import scala.concurrent.ExecutionContext

import javax.inject.Inject

class GetSubscriptionController @Inject() (cc: ControllerComponents, repository: PostSignupConfigRepository)(using
    ExecutionContext
) extends BackendController(cc) {
  def getSubscription(saoSubscriptionId: String): Action[AnyContent] = Action.async { implicit request =>
    repository.get(saoSubscriptionId).map {
      case Some(config) => handleConfig(config)
      case _            => Ok(Json.toJson(default200))
    }
  }

  private def handleConfig(config: PostSignupStubConfiguration): Result = {
    val configuration: Option[NoneDefaultApiConfiguration] = config.getSubscriptionResponseConfig
    val status: Int                                        = configuration.map(_.status).fold(OK)(identity)
    val body: String = configuration.flatMap(_.defaultBodyOverride).fold(Json.toJson(default200).toString)(identity)

    status match {
      case NO_CONTENT => NoContent
      case _          => Status(status)(body).as(JSON)
    }
  }

}

object GetSubscriptionController {
  extension (config: PostSignupStubConfiguration) {
    def getSubscriptionResponseConfig: Option[NoneDefaultApiConfiguration] =
      config.getSubscriptionAndPostRetrieveCustomerId.map {
        case GetSubscriptionOnlyConfig(status, defaultBodyOverride) =>
          NoneDefaultApiConfiguration(status, defaultBodyOverride)
        case PostRetrieveCustomerIdConfig(GetSubscriptionConfig(utr, crn, name, contacts), _, _) =>
          NoneDefaultApiConfiguration(
            status = OK,
            defaultBodyOverride = Some(
              Json
                .toJson(
                  GetSubscriptionResponse(
                    contacts = contacts,
                    nominatedCompany = Some(NominatedCompany(utr = Some(utr), crn = crn, name = name))
                  )
                )
                .toString
            )
          )
      }
  }

  def default200: GetSubscriptionResponse = GetSubscriptionResponse(
    etmpSafeId = Some("1234567890"),
    contacts = List(
      Contact(
        name = Some("Tester Eve"),
        email = Some("eve.tester@test.com"),
        language = Some("en"),
        status = Some("valid")
      ),
      Contact(
        name = Some("Tester Adams"),
        email = Some("admas.tester@test.com"),
        language = Some("cy"),
        status = Some("valid")
      )
    ),
    nominatedCompany = Some(
      NominatedCompany(
        crn = Some(generateCrn),
        name = Some("Fake Company Ltd"),
        utr = Some(generateUtr)
      )
    )
  )

}
