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

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{MimeTypes, Status}
import play.api.inject.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.*
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.{
  NoneDefaultApiConfiguration,
  PostSignupStubConfiguration
}
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.PostSignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.*

import scala.concurrent.Future

class NotificationControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with MockitoSugar {

  private val authHeader         = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val testSubscriptionId = "123"

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

  private val mockRepository = mock[PostSignupConfigRepository]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[PostSignupConfigRepository].toInstance(mockRepository))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
    when(mockRepository.get(any())).thenReturn(Future.successful(None))
  }

  "POST /subscriptions/:saoSubscriptionId/notifications" should {
    "return 201 and notification payload when PostSignupConfigRepository does not return a config for this endpoint" in {
      val result = routeResult(fakeNotificationPOSTRequest(testSubscriptionId, validNotificationRequest))

      status(result) shouldBe Status.CREATED
      contentAsString(result) should fullyMatch regex """^\{"notificationRef":"NOT[0-9]{10}"\}$"""
    }

    "return the configured status code and the default body when PostSignupConfigRepository returns a status only config for this endpoint" in {
      val testConfiguredStatus = Status.NOT_FOUND
      when(mockRepository.get(meq(testSubscriptionId))).thenReturn(
        Future.successful(
          Some(
            PostSignupStubConfiguration(
              subscriptionId = testSubscriptionId,
              postNotification = Some(NoneDefaultApiConfiguration(status = testConfiguredStatus))
            )
          )
        )
      )

      val result = routeResult(fakeNotificationPOSTRequest(testSubscriptionId, validNotificationRequest))
      status(result) shouldBe testConfiguredStatus
      contentAsString(result) should fullyMatch regex """^\{"notificationRef":"NOT[0-9]{10}"\}$"""
    }

    "return the configured status code and the configured body when PostSignupConfigRepository returns a config that has both for this endpoint" in {
      val testConfiguredStatus = Status.IM_A_TEAPOT

      val testConfigBody = "result"
      when(mockRepository.get(meq(testSubscriptionId))).thenReturn(
        Future.successful(
          Some(
            PostSignupStubConfiguration(
              subscriptionId = testSubscriptionId,
              postNotification = Some(
                NoneDefaultApiConfiguration(status = testConfiguredStatus, defaultBodyOverride = Some(testConfigBody))
              )
            )
          )
        )
      )

      val result = routeResult(fakeNotificationPOSTRequest(testSubscriptionId, validNotificationRequest))
      status(result) shouldBe testConfiguredStatus
      contentAsString(result) shouldBe testConfigBody
    }

    "return a structured 400 for a request with invalid JSON shape" in {
      val invalidNotificationRequest: JsValue = Json.obj(
        "companies" -> Json.arr("Test"),
        "saos"      -> Json.arr()
      )

      assertValidationError(
        testSubscriptionId,
        invalidNotificationRequest,
        Json.obj(
          "path"   -> "companies[0]",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for malformed JSON syntax" in {
      val fakePOSTRequest = FakeRequest("POST", s"/subscriptions/$testSubscriptionId/notifications")
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
        testSubscriptionId,
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
        testSubscriptionId,
        notificationRequestMissingRequiredField,
        Json.obj(
          "path"   -> "companies",
          "reason" -> "MISSING_REQUIRED_FIELD"
        )
      )
    }
  }
}
