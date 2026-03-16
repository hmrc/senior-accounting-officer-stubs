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
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class NotificationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val authHeader                = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val knownId                   = "123"
  private val unknownId                 = "567"
  def validNotificationRequest: JsValue = Json.parse(
    """
      |{
      |"companies": [
      |     {
      |     "companyName": "Example Ltd",
      |     "uniqueTaxReference": "1234567890",
      |     "companyReferenceNumber": "AB123456",
      |     "companyType": "LTD",
      |     "financialYearEndDate": "2024-12-31",
      |     "seniorAccountingOfficers": [
      |         {
      |         "name": "Firstname Lastname",
      |         "email": "Firstname.Lastname@example.com",
      |         "startDate": "2024-04-01",
      |         "endDate": "2025-03-31"
      |         },
      |         {
      |         "name": "Secondpersonname Theirlastname",
      |         "email": "nonemptyemail@companyname.com",
      |         "startDate": "2024-12-01",
      |         "endDate": "2025-12-31"
      |         }
      |       ]
      |      },
      |       {
      |         "companyName": "Example PLC",
      |         "uniqueTaxReference": "0987654321",
      |         "companyReferenceNumber": "CD654321",
      |         "companyType": "PLC",
      |         "financialYearEndDate": "2024-06-30",
      |         "seniorAccountingOfficers": [
      |         {
      |            "name": "Firstname Lastname",
      |            "email": "Firstname.Lastname@example.com",
      |            "startDate": "2024-04-01",
      |            "endDate": "2025-03-31"
      |         }
      |       ]
      |       }
      |   ],
      |"additionalInformation": "non-empty string"
      |}
      |""".stripMargin
  )

  def invalidNotificationRequest: JsValue = Json.parse(
    """
      |{
      | "companies": ["Test"]
      |}
      |""".stripMargin
  )

  "POST /notification/:saoSubscriptionId" should {
    "return 200 and notification payload for a known saoSubscriptionId" in {
      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(validNotificationRequest.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      val testNotificationResponse = Json.obj(
        "id"        -> "NOT0123456789",
        "timestamp" -> "2026-03-01T12:00:14Z"
      )

      status(result) shouldBe Status.OK

      contentAsJson(result) shouldBe testNotificationResponse
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val fakePOSTRequest = FakeRequest("POST", s"/notification/$unknownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(validNotificationRequest.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.NOT_FOUND
    }

    "return a structured 400 for a request with invalid JSON shape" in {
      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(invalidNotificationRequest.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path"   -> "companies[0]",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for malformed JSON syntax" in {
      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody("""{"companies":["Test"]""")

      val maybeResult = route(app, fakePOSTRequest)
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
