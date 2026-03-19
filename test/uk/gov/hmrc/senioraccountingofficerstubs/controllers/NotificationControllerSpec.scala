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
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class NotificationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val authHeader                = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val knownId                   = "123"
  private val unknownId                 = "567"

  private val validNotificationRequest: JsValue = Json.obj(
    "companies" -> Json.arr(
      Json.obj(
        "companyName" -> "Example Ltd",
        "uniqueTaxReference" -> "1234567890",
        "companyReferenceNumber" -> "AB123456",
        "companyType" -> "LTD",
        "financialYearEndDate" -> "2024-12-31",
        "seniorAccountingOfficers" -> Json.arr(
          Json.obj(
            "name" -> "Firstname Lastname",
            "email" -> "Firstname.Lastname@example.com",
            "startDate" -> "2024-04-01",
            "endDate" -> "2025-03-31"
          ),
          Json.obj(
            "name" -> "Secondpersonname Theirlastname",
            "email" -> "nonemptyemail@companyname.com",
            "startDate" -> "2024-12-01",
            "endDate" -> "2025-12-31"
          )
        )
      ),
      Json.obj(
        "companyName" -> "Example PLC",
        "uniqueTaxReference" -> "0987654321",
        "companyReferenceNumber" -> "CD654321",
        "companyType" -> "PLC",
        "financialYearEndDate" -> "2024-06-30",
        "seniorAccountingOfficers" -> Json.arr(
          Json.obj(
            "name" -> "Firstname Lastname",
            "email" -> "Firstname.Lastname@example.com",
            "startDate" -> "2024-04-01",
            "endDate" -> "2025-03-31"
          )
        )
      )
    ),
    "additionalInformation" -> "non-empty string"
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

    "return a structured 400 for constraint violation with invalid format" in {

      val notificationRequestInvalidFormat = Json.parse(
        validNotificationRequest.toString().replaceFirst(
          "Firstname\\.Lastname@example\\.com",
          "Firstname.Lastname example.com"
        )
      )

      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(notificationRequestInvalidFormat.toString)

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path" -> "companies[0].seniorAccountingOfficers[0].email",
          "reason" -> "INVALID_FORMAT"
        )
      )
    }

    "return a structured 400 for constraint violation with cannot be empty" in {

      val notificationRequestCannotBeEmpty = Json.parse(
        validNotificationRequest.toString().replaceFirst(
          "Example Ltd",
          ""
        )
      )

      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(notificationRequestCannotBeEmpty.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path" -> "companies[0].companyName",
          "reason" -> "CANNOT_BE_EMPTY"
        )
      )
    }

    "return a structured 400 for constraint violation with invalid data type" in {

      val notificationRequestInvalidDataType = Json.parse(
        """
          |{
          |"companies": [
          |     {
          |     "companyName": 123,
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

//      val notificationRequestInvalidDataType2 = Json.parse(
//        validNotificationRequest.toString().replaceFirst(
//          "Example Ltd",
//          123
//        )
//      )

      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(notificationRequestInvalidDataType.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path" -> "companies[0].companyName",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for constraint violation with invalid data type when there is an additional json property" in {

      val additionalProperty: JsObject = Json.obj("extraProperty" -> "I shouldn't be here")
      val notificationRequestExtraProperty = validNotificationRequest.as[JsObject] ++ additionalProperty

      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(notificationRequestExtraProperty.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path" -> "extraProperty",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for constraint violation with array min items not met" in {

      val notificationRequestArrayMinItemsNotMet: JsValue = Json.obj(
        "companies" -> Json.arr(),
        "additionalInformation" -> "non-empty string"
      )

      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(notificationRequestArrayMinItemsNotMet.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path" -> "companies",
          "reason" -> "ARRAY_MIN_ITEMS_NOT_MET"
        )
      )
    }

    "return a structured 400 for constraint violation with length out of bounds" in {

      val notificationRequestLengthOutOfBounds = Json.parse(
        validNotificationRequest.toString().replaceFirst(
          "non-empty string",
          "non-empty string " * 300
        )
      )

      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(notificationRequestLengthOutOfBounds.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path" -> "additionalInformation",
          "reason" -> "LENGTH_OUT_OF_BOUNDS"
        )
      )
    }

    "return a structured 400 for constraint violation with invalid enum" in {

      val notificationRequestInvalidEnum = Json.parse(
        validNotificationRequest.toString().replaceFirst(
          "LTD",
          "LDX"
        )
      )

      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(notificationRequestInvalidEnum.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path" -> "companies[0].companyType",
          "reason" -> "INVALID_ENUM_VALUE"
        )
      )
    }

    "return a structured 400 for constraint violation with missing required field" in {

      val notificationRequestMissingRequiredField = validNotificationRequest.as[JsObject] - "companies"

      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody(notificationRequestMissingRequiredField.toString())

      val maybeResult = route(app, fakePOSTRequest)
      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path" -> "companies",
          "reason" -> "MISSING_REQUIRED_FIELD"
        )
      )
    }
  }
}
