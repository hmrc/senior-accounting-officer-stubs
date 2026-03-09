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
import play.api.http.{MimeTypes, Status}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}

class NotificationControllerSpec extends AnyWordSpec with Matchers {

  private val controller     = new NotificationController(Helpers.stubControllerComponents())

  private val knownId   = "123"
  private val unknownId = "567"
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
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON)
        .withBody(validNotificationRequest)

      val result = controller.postNotification(knownId)(fakePOSTRequest)

      status(result) shouldBe Status.OK

      contentAsJson(result) shouldBe Json.obj(
        "id" -> "NOT0123456789",
        "timestamp" -> "2026-03-01T12:00:14Z"
      )
    }

    "return a 404 for an unknown saoSubscriptionId" in {
        val fakePOSTRequest = FakeRequest("POST", s"/notification/$unknownId")
          .withHeaders(CONTENT_TYPE -> MimeTypes.JSON)
          .withBody(validNotificationRequest)

        val result = controller.postNotification(unknownId)(fakePOSTRequest)

        status(result) shouldBe Status.NOT_FOUND
      }

    "return a 400 for a request with invalid Json" in {
      val fakePOSTRequest = FakeRequest("POST", s"/notification/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON)
        .withBody(invalidNotificationRequest)

      val result = controller.postNotification(knownId)(fakePOSTRequest)

      status(result) shouldBe Status.BAD_REQUEST
    }
    }
  }