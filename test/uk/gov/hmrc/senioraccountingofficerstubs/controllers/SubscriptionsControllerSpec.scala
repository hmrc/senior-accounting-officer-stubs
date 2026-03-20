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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.{MimeTypes, Status}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future

class SubscriptionsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val knownId                  = "123"
  private val unknownId                = "567"
  private val authHeader               = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val validSubscriptionRequest = Json.obj(
    "safeId"  -> "XE000123456789",
    "company" -> Json.obj(
      "companyName"               -> "Acme Manufacturing Ltd",
      "uniqueTaxReference"        -> "1234567890",
      "companyRegistrationNumber" -> "OC123456"
    ),
    "contacts" -> Json.arr(
      Json.obj("name" -> "Jane Doe", "email" -> "jane.doe@example.com")
    )
  )

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  private def fakeSubscriptionsPUTRequest(id: String, payload: JsValue) =
    FakeRequest("PUT", s"/subscriptions/$id")
      .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
      .withTextBody(payload.toString())

  private def assertValidationError(id: String, payload: JsValue, expectedError: play.api.libs.json.JsValue): Unit = {
    val result = routeResult(fakeSubscriptionsPUTRequest(id, payload))
    status(result) shouldBe Status.BAD_REQUEST
    contentAsJson(result) shouldBe Json.arr(expectedError)
  }

  "PUT /subscriptions" should {
    "return 200 for a valid request payload" in {
      val result = routeResult(fakeSubscriptionsPUTRequest(knownId, validSubscriptionRequest))

      val testSubscriptionResponse = Json.obj(
        "saoSubscriptionId"     -> "123",
        "subscriptionTimestamp" -> "2026-03-01T12:00:14Z"
      )

      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe testSubscriptionResponse

    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val result = routeResult(fakeSubscriptionsPUTRequest(unknownId, validSubscriptionRequest))

      status(result) shouldBe Status.NOT_FOUND
    }

    "return a structured 400 for constraint violation with malformed request when JSON syntax is incorrect" in {
      val fakeRequest = FakeRequest("PUT", s"/subscriptions/$knownId")
        .withHeaders(CONTENT_TYPE -> "application/json", AUTHORIZATION -> authHeader)
        .withTextBody("""{"subscription":""")

      val result = routeResult(fakeRequest)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("reason" -> "MALFORMED_REQUEST")
      )
    }

    "return a structured 400 for constraint violation with invalid format" in {
      val subscriptionRequestInvalidFormat = Json.parse(
        validSubscriptionRequest
          .toString()
          .replaceFirst(
            "XE000123456789",
            "bad safe id"
          )
      )

      assertValidationError(
        knownId,
        subscriptionRequestInvalidFormat,
        Json.obj(
          "path"   -> "safeId",
          "reason" -> "INVALID_FORMAT"
        )
      )
    }

    "return a structured 400 for constraint violation with cannot be empty" in {
      val subscriptionRequestCannotBeEmpty = Json.parse(
        validSubscriptionRequest
          .toString()
          .replaceFirst(
            "Acme Manufacturing Ltd",
            ""
          )
      )

      assertValidationError(
        knownId,
        subscriptionRequestCannotBeEmpty,
        Json.obj(
          "path"   -> "company.companyName",
          "reason" -> "CANNOT_BE_EMPTY"
        )
      )
    }

    "return a structured 400 for constraint violation with invalid data type when the request is an invalid JSON object" in {
      val invalidSubscriptionRequest: JsValue = Json.obj(
        "safeId" -> Json.arr("Invalid")
      )

      val result = routeResult(fakeSubscriptionsPUTRequest(knownId, invalidSubscriptionRequest))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path"   -> "company",
          "reason" -> "MISSING_REQUIRED_FIELD"
        ),
        Json.obj(
          "path"   -> "contacts",
          "reason" -> "MISSING_REQUIRED_FIELD"
        ),
        Json.obj(
          "path"   -> "safeId",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for constraint violation with invalid data type when there is an additional json property" in {
      val additionalProperty: JsObject     = Json.obj("extraProperty" -> "I shouldn't be here")
      val subscriptionRequestExtraProperty = validSubscriptionRequest.as[JsObject] ++ additionalProperty

      assertValidationError(
        knownId,
        subscriptionRequestExtraProperty,
        Json.obj(
          "path"   -> "extraProperty",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for constraint violation with array min items not met" in {
      val subscriptionRequestArrayMinItemsNotMet: JsValue = Json.obj(
        "safeId"  -> "XE000123456789",
        "company" -> Json.obj(
          "companyName"               -> "Acme Manufacturing Ltd",
          "uniqueTaxReference"        -> "1234567890",
          "companyRegistrationNumber" -> "OC123456"
        ),
        "contacts" -> Json.arr()
      )

      assertValidationError(
        knownId,
        subscriptionRequestArrayMinItemsNotMet,
        Json.obj(
          "path"   -> "contacts",
          "reason" -> "ARRAY_MIN_ITEMS_NOT_MET"
        )
      )
    }

    "return a structured 400 for constraint violation with length out of bounds" in {
      val subscriptionRequestLengthOutOfBounds = Json.parse(
        validSubscriptionRequest
          .toString()
          .replaceFirst(
            "Acme Manufacturing Ltd",
            "Acme Manufacturing Ltd " * 300
          )
      )

      assertValidationError(
        knownId,
        subscriptionRequestLengthOutOfBounds,
        Json.obj(
          "path"   -> "company.companyName",
          "reason" -> "LENGTH_OUT_OF_BOUNDS"
        )
      )
    }

    "return a structured 400 for constraint violation with missing required field" in {

      val subscriptionRequestMissingRequiredField = validSubscriptionRequest.as[JsObject] - "safeId"

      assertValidationError(
        knownId,
        subscriptionRequestMissingRequiredField,
        Json.obj(
          "path"   -> "safeId",
          "reason" -> "MISSING_REQUIRED_FIELD"
        )
      )
    }

  }
}
