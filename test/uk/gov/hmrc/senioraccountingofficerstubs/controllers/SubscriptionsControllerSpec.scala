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
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class SubscriptionsControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val knownId      = "123"
  private val authHeader   = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val validPayload = Json.obj(
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

  "PUT /subscriptions" should {
    "return 200 for a valid request payload" in {
      val request = FakeRequest("PUT", s"/subscriptions/$knownId")
        .withHeaders(CONTENT_TYPE -> "application/json", AUTHORIZATION -> authHeader)
        .withTextBody(validPayload.toString())

      val maybeResult = route(app, request)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      val testSubscriptionResponse = Json.obj(
        "saoSubscriptionId" -> "123",
        "timestamp"         -> "2026-03-01T12:00:14Z"
      )

      status(result) shouldBe Status.OK

      contentAsJson(result) shouldBe testSubscriptionResponse

    }

    "return 400 when the request payload is an empty object" in {
      val request = FakeRequest("PUT", s"/subscriptions/$knownId")
        .withHeaders(CONTENT_TYPE -> "application/json", AUTHORIZATION -> authHeader)
        .withTextBody(Json.obj().toString())

      val maybeResult = route(app, request)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "company", "reason"  -> "MISSING_REQUIRED_FIELD"),
        Json.obj("path" -> "contacts", "reason" -> "MISSING_REQUIRED_FIELD"),
        Json.obj("path" -> "safeId", "reason"   -> "MISSING_REQUIRED_FIELD")
      )
    }

    "return 400 when the request payload is not a JSON object" in {
      val request = FakeRequest("PUT", s"/subscriptions/$knownId")
        .withHeaders(CONTENT_TYPE -> "application/json", AUTHORIZATION -> authHeader)
        .withTextBody(Json.arr("invalid").toString())

      val maybeResult = route(app, request)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path"   -> "body",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return 400 when a field fails contract validation" in {
      val request = FakeRequest("PUT", s"/subscriptions/$knownId")
        .withHeaders(CONTENT_TYPE -> "application/json", AUTHORIZATION -> authHeader)
        .withTextBody(
          Json
            .obj(
              "safeId"  -> "bad safe id",
              "company" -> Json.obj(
                "companyName"               -> "Acme Manufacturing Ltd",
                "uniqueTaxReference"        -> "ABC",
                "companyRegistrationNumber" -> "BAD"
              ),
              "contacts" -> Json.arr(
                Json.obj("name" -> "", "email" -> "not-an-email")
              )
            )
            .toString()
        )

      val maybeResult = route(app, request)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("path" -> "company.companyRegistrationNumber", "reason" -> "INVALID_FORMAT"),
        Json.obj("path" -> "company.uniqueTaxReference", "reason"        -> "INVALID_FORMAT"),
        Json.obj("path" -> "contacts[0].email", "reason"                 -> "INVALID_FORMAT"),
        Json.obj("path" -> "contacts[0].name", "reason"                  -> "CANNOT_BE_EMPTY"),
        Json.obj("path" -> "safeId", "reason"                            -> "INVALID_FORMAT")
      )
    }

    "return 400 when the request payload is malformed JSON" in {
      val request = FakeRequest("PUT", s"/subscriptions/$knownId")
        .withHeaders(CONTENT_TYPE -> "application/json", AUTHORIZATION -> authHeader)
        .withTextBody("""{"subscription":""")

      val maybeResult = route(app, request)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("reason" -> "MALFORMED_REQUEST")
      )
    }
  }
}
