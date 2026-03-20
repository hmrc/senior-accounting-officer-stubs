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
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future

class ContactDetailsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val authHeader     = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val fakeGETRequest = FakeRequest("GET", "/contact-details")
  private val controller     = app.injector.instanceOf[ContactDetailsController]

  private val knownId   = "123"
  private val unknownId = "567"

  private val validContactDetailsRequest: JsValue = Json.arr(
    Json.obj(
      "name" -> "Jane Doe",
      "email" -> "jane.doe@example.com"
    )
  )

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None => fail("Expected route to be defined")
    }

  private def fakeContactDetailsPUTRequest(id: String, payload: JsValue) =
    FakeRequest("PUT", s"/contact-details/$id")
      .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
      .withTextBody(payload.toString())

  private def assertValidationError(id: String, payload: JsValue, expectedError: play.api.libs.json.JsValue): Unit = {
    val result = routeResult(fakeContactDetailsPUTRequest(id, payload))
    status(result) shouldBe Status.BAD_REQUEST
    contentAsJson(result) shouldBe Json.arr(expectedError)
  }

  "GET /contact-details/:saoSubscriptionId" should {
    "return 200 and contact details for a known saoSubscriptionId" in {
      val result = controller.getContactDetails(knownId)(fakeGETRequest)

      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.obj(
        "name"  -> "Jane Doe",
        "email" -> "jane.doe@acme.example"
      )
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val result = controller.getContactDetails(unknownId)(fakeGETRequest)
      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "PUT /contact-details/:saoSubscriptionId" should {
    "return 204 for a known saoSubscriptionId with a valid payload" in {
      val result = routeResult(fakeContactDetailsPUTRequest(knownId, validContactDetailsRequest))
      status(result) shouldBe Status.NO_CONTENT
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val result = routeResult(fakeContactDetailsPUTRequest(unknownId, validContactDetailsRequest))
      status(result) shouldBe Status.NOT_FOUND
    }

    "return a structured 400 for constraint violation with malformed request when JSON syntax is incorrect" in {
      val fakePUTRequest = FakeRequest("PUT", s"/contact-details/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody("""[{"name":"Jane Doe"}""")

      val result = routeResult(fakePUTRequest)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("reason" -> "MALFORMED_REQUEST")
      )
    }

    "return a structured 400 for constraint violation with invalid data type when the request payload is not an array" in {
      val invalidContactDetailsRequest: JsValue = Json.obj(
          "name" -> "Jane Doe",
          "email" -> "jane.doe@example.com"
      )

      val result = routeResult(fakeContactDetailsPUTRequest(knownId, invalidContactDetailsRequest))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "body", "reason" -> "INVALID_DATA_TYPE")
      )
    }

    "return a structured 400 for constraint violation with missing required field" in {
      val contactDetailsRequestMissingRequiredField: JsValue = Json.arr(
        Json.obj("email" -> "jane.doe@example.com")
      )

      assertValidationError(
        knownId,
        contactDetailsRequestMissingRequiredField,
        Json.obj("path" -> "[0].name", "reason" -> "MISSING_REQUIRED_FIELD")
      )
    }

    "return a structured 400 for constraint violation with invalid format" in {
      val contactDetailsRequestInvalidFormat: JsValue = Json.arr(
        Json.obj(
          "name" -> "Jane Doe",
          "email" -> "jane.doe example.com"
        )
      )

      assertValidationError(
        knownId,
        contactDetailsRequestInvalidFormat,
        Json.obj(
          "path"   -> "[0].email",
          "reason" -> "INVALID_FORMAT"
        )
      )
    }

    "return a structured 400 for constraint violation with cannot be empty" in {

      val contactDetailsRequestCannotBeEmpty: JsValue = Json.arr(
        Json.obj(
          "name" -> "",
          "email" -> "jane.doe@example.com"
        )
      )

      assertValidationError(
        knownId,
        contactDetailsRequestCannotBeEmpty,
        Json.obj(
          "path"   -> "[0].name",
          "reason" -> "CANNOT_BE_EMPTY"
        )
      )
    }

    "return a structured 400 for constraint violation with array min items not met" in {

      val contactDetailsRequestArrayMinItemsNotMet: JsValue = Json.arr()

      assertValidationError(
        knownId,
        contactDetailsRequestArrayMinItemsNotMet,
        Json.obj(
          "path"   -> "body",
          "reason" -> "ARRAY_MIN_ITEMS_NOT_MET"
        )
      )
    }

    "return a structured 400 for constraint violation with length out of bounds" in {
      val contactDetailsLengthOutOfBounds: JsValue = Json.arr(
        Json.obj(
          "name" -> "Jane Doe" * 300,
          "email" -> "jane.doe@example.com"
        )
      )

      assertValidationError(
        knownId,
        contactDetailsLengthOutOfBounds,
        Json.obj(
          "path"   -> "[0].name",
          "reason" -> "LENGTH_OUT_OF_BOUNDS"
        )
      )
    }

  }
}
