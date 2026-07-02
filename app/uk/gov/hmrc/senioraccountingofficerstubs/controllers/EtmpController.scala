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

import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.{EtmpHelper, JsonErrorHandling}
import uk.gov.hmrc.senioraccountingofficerstubs.models.{EtmpSuccessResponse, Success as EtmpSuccess}

import scala.util.Random

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
class EtmpController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  def createEtmp: Action[String] = Action(parse.tolerantText) { implicit request =>
    val correlationId = request.headers
      .get("correlationid").getOrElse("")
    if correlationId == "" then BadRequest
    if EtmpHelper.validateHeaders(request.headers) then {
      JsonErrorHandling.parseJson(request.body) match {
        case Right(json) => {
          val errors = JsonErrorHandling.Validators.validateEtmp(json)
          if errors.nonEmpty then JsonErrorHandling.badRequest(errors)
          else {
            val etmpSuccess =
              EtmpSuccess(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString, f"XB${Random.nextInt(1000000)}%013d")
            val response = EtmpSuccessResponse(etmpSuccess)
            Created(Json.toJson(response)).withHeaders("correlationid" -> correlationId)
          }
        }
        case Left(errorResult) => errorResult
      }
    } else BadRequest("missing or invalid headers")
  }

}
