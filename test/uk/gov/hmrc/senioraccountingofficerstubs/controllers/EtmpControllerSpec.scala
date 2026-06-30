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

import org.scalactic.Prettifier.default
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.{MimeTypes, Status}
import play.api.libs.json.*
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future
import scala.util.Random

import java.util.UUID

class EtmpControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val authHeader = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  app.injector.instanceOf[EtmpController]

  private val validEtmpRequest: JsValue = Json.obj(
    "idType"   -> "UTR",
    "idNumber" -> generateIdNumber
  )

  private val X_TRANSMITTING_SYSTEM = ("X-Transmitting-System" -> "HIP")
  private val X_ORIGINATING_SYSTEM  = ("X-Originating-System"  -> "MDTP")
  private val CORRELATION_ID        = ("correlationid"         -> generateCorrelationId)
  private val X_RECEIPT_DATE        = ("X-Receipt-Date"        -> "2026-05-05T12:05:45Z")

  private def generateCorrelationId: String =
    UUID.randomUUID().toString

  private def generateIdNumber: String =
    val num = Random.nextInt(100000)
    f"$num%010d"

  private def fakeEtmpPOSTRequest(payload: JsValue) = {
    FakeRequest("POST", "/RESTAdapter/dsao/subscription").withHeaders(
      CONTENT_TYPE   -> MimeTypes.JSON,
      (AUTHORIZATION -> authHeader),
      X_TRANSMITTING_SYSTEM,
      X_ORIGINATING_SYSTEM,
      CORRELATION_ID,
      X_RECEIPT_DATE
    )
  }
    .withTextBody(payload.toString)

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match
      case Some(value) => value
      case None        => fail("Expected route to be defined")

  "POST /RESTAdapter/dsao/subscription" should {
    "return 201 and Etmp success response for an idNumber" in {
      val result           = routeResult(fakeEtmpPOSTRequest(validEtmpRequest))
      val expectedResponse =
        """^\{"success":\{"processingDate":"[0-9]{4}-([0][1-9]|[1][0-2])-([0][1-9]|[1-2][0-9]|[3][0-1])T([0-1][0-9]|[2][0-3]):[0-5][0-9]:[0-5][0-9]Z","dsaoIdNumber":"XB[0-9]{1,15}"\}}$"""
      status(result) shouldBe Status.CREATED
      contentAsString(result) should fullyMatch regex expectedResponse
    }

    "return code 400 for a request without a required header, correlationid" in {
      val requestWithoutCorrelationId = FakeRequest("POST", "/RESTAdapter/dsao/subscription")
        .withHeaders(
          CONTENT_TYPE   -> MimeTypes.JSON,
          (AUTHORIZATION -> authHeader),
          X_TRANSMITTING_SYSTEM,
          X_ORIGINATING_SYSTEM,
          X_RECEIPT_DATE
        )
        .withTextBody(validEtmpRequest.toString)

      val result = routeResult(requestWithoutCorrelationId)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "missing or invalid headers"
    }

    "return a structured 400 for a request with invalid enum for idType" in {
      val invalidPayload: JsValue = Json.obj(
        "idNumber" -> generateIdNumber,
        "idType"   -> "Test"
      )
      val expectedContent = Json.arr(Json.obj("path" -> "idType", "reason" -> "INVALID_ENUM_VALUE"))
      val result          = routeResult(fakeEtmpPOSTRequest(invalidPayload))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe expectedContent
    }

    "return a structured 400 for when any extra fields are added" in {
      val invalidPayload = validEtmpRequest.as[JsObject] ++ Json.obj("extra" -> "value")
      val expectedPayload = """[{"path":"extra","reason":"INVALID_DATA_TYPE"}]"""

      val result = routeResult(fakeEtmpPOSTRequest(invalidPayload))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedPayload
    }

    "return a structured 400 for constraint violation with missing required field" in {
      val invalidPayload = validEtmpRequest.as[JsObject] - "idType"
      val result         = routeResult(fakeEtmpPOSTRequest(invalidPayload))
      val expectedPayload = Json.arr(Json.obj("path" -> "idType", "reason" -> "MISSING_REQUIRED_FIELD")).toString
      println(expectedPayload)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedPayload
    }

    "return a structured 400 for a malformed request" in {
      val expectedPayload = Json.arr(Json.obj("reason" -> "MALFORMED_REQUEST")).toString
      val result = routeResult(fakeEtmpPOSTRequest(validEtmpRequest).withTextBody("("))
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedPayload
    }

  }
}
