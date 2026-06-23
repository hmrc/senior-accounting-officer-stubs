/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.{MimeTypes, Status}
import play.api.libs.json.*
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.domain.SaUtrGenerator

import scala.concurrent.Future
import scala.util.Random

class NotificationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val authHeader = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val knownId    = "123"
  private val unknownId  = "567"

  private val validNotificationRequest: JsValue = Json.obj(
    "companies" -> Json.arr(
      Json.obj(
        "name"         -> "Example Ltd",
        "utr"          -> generateUtr,
        "crn"          -> generateCrn,
        "type"         -> "LTD",
        "accPeriodEnd" -> "2024-12-31",
        "status"       -> "pass"
      ),
      Json.obj(
        "name"         -> "Example Ltd",
        "utr"          -> generateUtr,
        "crn"          -> generateCrn,
        "type"         -> "PLC",
        "accPeriodEnd" -> "2024-06-30",
        "status"       -> "pass"
      )
    ),
    "saos" -> Json.arr(
      Json.obj(
        "name"     -> "Firstname Lastname",
        "email"    -> "Firstname.Lastname@example.com",
        "fromDate" -> "2024-04-01",
        "toDate"   -> "2025-03-31"
      )
    )
  )

  private def generateCrn = {
    val num = Random.nextInt(1000000)
    f"$num%010d"
  }

  private def generateUtr = {
    val seed = Random.nextInt(1000000)
    SaUtrGenerator(seed).nextSaUtr
  }

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  private def fakeNotificationPOSTRequest(id: String, payload: JsValue) =
    FakeRequest("POST", s"/subscriptions/$id/notifications")
      .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
      .withTextBody(payload.toString())

  private def assertValidationError(id: String, payload: JsValue, expectedError: JsValue): Unit = {
    val result = routeResult(fakeNotificationPOSTRequest(id, payload))
    status(result) shouldBe Status.BAD_REQUEST
    contentAsJson(result) shouldBe Json.arr(expectedError)
  }

  "POST /subscriptions/:saoSubscriptionId/notifications" should {
    "return 200 and notification payload for a known saoSubscriptionId" in {
      val result = routeResult(fakeNotificationPOSTRequest(knownId, validNotificationRequest))

      status(result) shouldBe Status.OK
      contentAsString(result) should fullyMatch regex """^\{"notificationRef":"NOT[0-9]{10}"\}$"""
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val result = routeResult(fakeNotificationPOSTRequest(unknownId, validNotificationRequest))
      status(result) shouldBe Status.NOT_FOUND
    }

    "return a structured 400 for a request with invalid JSON shape" in {
      val invalidNotificationRequest: JsValue = Json.obj(
        "companies" -> Json.arr("Test"),
        "saos"      -> Json.arr()
      )

      assertValidationError(
        knownId,
        invalidNotificationRequest,
        Json.obj(
          "path"   -> "companies[0]",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for malformed JSON syntax" in {
      val fakePOSTRequest = FakeRequest("POST", s"/subscriptions/$knownId/notifications")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody("""{"companies":["Test"]""")

      val result = routeResult(fakePOSTRequest)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("reason" -> "MALFORMED_REQUEST")
      )
    }

    "return a structured 400 for constraint violation with invalid format" in {
      val notificationRequestInvalidFormat = Json.parse(
        validNotificationRequest
          .toString()
          .replaceFirst(
            "Firstname\\.Lastname@example\\.com",
            "Firstname.Lastname example.com"
          )
      )

      assertValidationError(
        knownId,
        notificationRequestInvalidFormat,
        Json.obj(
          "path"   -> "saos[0].email",
          "reason" -> "INVALID_FORMAT"
        )
      )
    }

    "return a structured 400 for constraint violation with missing required field" in {
      val notificationRequestMissingRequiredField = validNotificationRequest.as[JsObject] - "companies"

      assertValidationError(
        knownId,
        notificationRequestMissingRequiredField,
        Json.obj(
          "path"   -> "companies",
          "reason" -> "MISSING_REQUIRED_FIELD"
        )
      )
    }
  }
}
