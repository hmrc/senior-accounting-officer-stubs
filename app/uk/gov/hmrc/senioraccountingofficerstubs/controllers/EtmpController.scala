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
import play.api.mvc.Headers
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.senioraccountingofficerstubs.helpers.JsonErrorHandling
import uk.gov.hmrc.senioraccountingofficerstubs.models.{EtmpSuccessResponse, Success as EtmpSuccess}

import scala.util.{Random, Try}

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
class EtmpController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val headers = Seq("X-Transmitting-System", "X-Originating-System", "correlationid", "X-Receipt-Date")

  def generateDsaoIdNumber: String = {
    val num = Random.nextInt(1000000)
    f"XB$num%013d"
  }

  def createEtmp: Action[String] = Action(parse.tolerantText) { implicit request =>
    if validateHeaders(request.headers) != true then BadRequest("issues with headers")
    else
      JsonErrorHandling.parseJson(request.body) match {
        case Right(json) =>
          val errors = JsonErrorHandling.Validators.validateEtmp(json)
          if errors.nonEmpty then JsonErrorHandling.badRequest(errors)
          else {
            val etmpSuccess = EtmpSuccess(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString, generateDsaoIdNumber)
            val response    = EtmpSuccessResponse(etmpSuccess)
            Created(Json.toJson(response))
          }
        case Left(errorResult) => errorResult
      }
  }

  private def validateHeaders(request_headers: Headers): Boolean = {

    val headers_map = headers.foldLeft(Map.empty[String, String]) { (map, header) =>
      request_headers.get(header) match {
        case Some(header_val) => map + (header -> header_val)
        case None             => map
      }
    }

    if headers_map.size != headers.size then false
    else {
      val isValidTransmittingSystem = headers_map.get("X-Transmitting-System") match {
        case Some("HIP") => true
        case _           => false
      }

      val isValidOriginatingSystem =
        headers_map.get("X-Originating-System").exists(value => value.length >= 1 && value.length <= 30)

      val isValidCorrelationId = headers_map
        .get("correlationid")
        .exists(correlationId =>
          correlationId.matches("^[0-9a-fA-F]{8}[-][0-9a-fA-F]{4}[-][0-9a-fA-F]{4}[-][0-9a-fA-F]{4}[-][0-9a-fA-F]{12}$")
        )

      val isValidReceiptDate = headers_map
        .get("X-Receipt-Date")
        .exists(datetime => {
          Try(Instant.parse(datetime)).toEither.fold(
            error => false,
            datetime => true
          )
        })
      isValidTransmittingSystem && isValidOriginatingSystem && isValidCorrelationId && isValidReceiptDate
    }

  }

}
