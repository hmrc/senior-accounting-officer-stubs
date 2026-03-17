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
import play.api.libs.json.JsArray
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class ObligationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val knownId    = "123"
  private val unknownId  = "567"
  private val authHeader = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"

  "GET /obligation/:saoSubscriptionId" should {
    "return 200 and obligation for a known saoSubscriptionId" in {
      val request = FakeRequest("GET", s"/obligation/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)

      val maybeResult = route(app, request)

      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.OK
      val json = contentAsJson(result)

      val subscription = json \ "subscription"
      (subscription \ "subscriptionTimestamp").as[String] shouldBe "2021-01-01T00:00:00Z"
      (subscription \ "companyRegistrationNumber").as[String] should fullyMatch regex """\d{10}"""
      (subscription \ "uniqueTaxReference").as[String] should fullyMatch regex """\d{10}"""
      (subscription \ "companyName").as[String] shouldBe "Testdata Company Ltd"

      val contacts = (subscription \ "contacts").as[JsArray]
      (contacts(0) \ "name").as[String] shouldBe "Firstname Middlename Lastname"
      (contacts(0) \ "email").as[String] shouldBe "example@example.com"

      val submissions     = (json \ "submissions").as[JsArray]
      val firstSubmission = submissions(0)
      (firstSubmission \ "financialYearEnd").as[Int] shouldBe 2025

      val notification = firstSubmission \ "notification"
      (notification \ "id").as[String] shouldBe "notificationId"
      (notification \ "notificationTimestamp").as[String] shouldBe "2021-01-01T00:00:00Z"

      val certificate = firstSubmission \ "certificate"
      (certificate \ "id").as[String] shouldBe "certificateId"
      (certificate \ "certificateTimestamp").as[String] shouldBe "2021-01-01T00:00:00Z"
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val request = FakeRequest("GET", s"/obligation/$unknownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)

      val maybeResult = route(app, request)

      maybeResult shouldBe defined
      val result = maybeResult match {
        case Some(value) => value
        case None        => fail("Expected route to be defined")
      }

      status(result) shouldBe Status.NOT_FOUND
    }
  }
}
