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

class CertificateControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with MockitoSugar {

  private val authHeader             = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val testSubscriptionId     = "123"
  private val testLongSubscriptionId = "1234567890123456"

  private val validCertificateRequest: JsValue = Json.obj(
    "submitterName" -> "Jane Smith",
    "saoName"       -> "Jane Smith",
    "saoEmail"      -> "Firstname.Lastname@example.com",
    "companies"     -> Json.arr(
      Json.obj(
        "crn"                            -> generateCrn,
        "utr"                            -> generateUtr,
        "name"                           -> "Example Subsidiary Ltd",
        "accPeriodEnd"                   -> "2025-03-31",
        "status"                         -> "COMPLIANT",
        "type"                           -> "LTD",
        "isCorporationTaxQualified"      -> true,
        "isVatQualified"                 -> true,
        "isPayeQualified"                -> true,
        "isInsurancePremiumTaxQualified" -> false,
        "isStampDutyLandTaxQualified"    -> false,
        "isStampDutyReserveTaxQualified" -> false,
        "isPetroleumRevenueTaxQualified" -> false,
        "isCustomsDutiesQualified"       -> false,
        "isExciseDutiesQualified"        -> false,
        "isBankLevyQualified"            -> false
      )
    )
  )

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  private def fakeCertificatePOSTRequest(id: String, payload: JsValue) =
    FakeRequest("POST", s"/subscriptions/$id/certificates")
      .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
      .withTextBody(payload.toString())

  private def assertValidationError(id: String, payload: JsValue, expectedError: JsValue): Unit = {
    val result = routeResult(fakeCertificatePOSTRequest(id, payload))
    status(result) shouldBe Status.BAD_REQUEST
    contentAsJson(result) shouldBe expectedError
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

  "POST /subscriptions/:saoSubscriptionId/certificates" should {
    "return 201 and certificate payload when PostSignupConfigRepository does not return a config for this endpoint" in {
      val result = routeResult(fakeCertificatePOSTRequest(testSubscriptionId, validCertificateRequest))

      status(result) shouldBe Status.CREATED
      contentAsString(result) should fullyMatch regex """^\{"certificateRef":"CRT[0-9]{10}"\}$"""
    }

    "return the configured status code and the default body when PostSignupConfigRepository returns a status only config for this endpoint" in {
      val testConfiguredStatus = Status.NOT_FOUND
      when(mockRepository.get(meq(testSubscriptionId))).thenReturn(
        Future.successful(
          Some(
            PostSignupStubConfiguration(
              subscriptionId = testSubscriptionId,
              postCertificate = Some(NoneDefaultApiConfiguration(status = testConfiguredStatus))
            )
          )
        )
      )

      val result = routeResult(fakeCertificatePOSTRequest(testSubscriptionId, validCertificateRequest))
      status(result) shouldBe testConfiguredStatus
      contentAsString(result) should fullyMatch regex """^\{"certificateRef":"CRT[0-9]{10}"\}$"""
    }

    "return the configured status code and the configured body when PostSignupConfigRepository returns a config that has both for this endpoint" in {
      val testConfiguredStatus = Status.IM_A_TEAPOT

      val testConfigBody = "result"
      when(mockRepository.get(meq(testSubscriptionId))).thenReturn(
        Future.successful(
          Some(
            PostSignupStubConfiguration(
              subscriptionId = testSubscriptionId,
              postCertificate = Some(
                NoneDefaultApiConfiguration(status = testConfiguredStatus, defaultBodyOverride = Some(testConfigBody))
              )
            )
          )
        )
      )

      val result = routeResult(fakeCertificatePOSTRequest(testSubscriptionId, validCertificateRequest))
      status(result) shouldBe testConfiguredStatus
      contentAsString(result) shouldBe testConfigBody
    }

    "return a structured 400 for a request with invalid JSON shape" in {

      val invalidCertificateRequest: JsValue = Json.obj(
        "companies" -> Json.arr("Test")
      )

      val result = routeResult(fakeCertificatePOSTRequest(testSubscriptionId, invalidCertificateRequest))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj(
          "failures" -> Json.arr(
            Json.obj("type" -> "INVALID_DATA_TYPE", "reason" -> "companies[0]"),
            Json.obj(
              "type"   -> "MISSING_REQUIRED_FIELD",
              "reason" -> "saoEmail"
            ),
            Json.obj(
              "type"   -> "MISSING_REQUIRED_FIELD",
              "reason" -> "saoName"
            )
          )
        )
      )
    }

    "return 400 for a subscriptionId that is more than 15 characters long" in {

      val result = routeResult(fakeCertificatePOSTRequest(testLongSubscriptionId, validCertificateRequest))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj(
          "failures" -> Json.arr(
            Json.obj(
              "type"   -> "LENGTH_OUT_OF_BOUNDS",
              "reason" -> "subscriptionId"
            )
          )
        )
      )
    }

    "return a structured 400 for constraint violation with malformed request when JSON syntax is incorrect" in {
      val fakePOSTRequest = FakeRequest("POST", s"/subscriptions/$testSubscriptionId/certificates")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody("""{"companies":["Test"]""")

      val result = routeResult(fakePOSTRequest)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj(
          "failures" -> Json.arr(
            Json.obj(
              "type"   -> "MALFORMED_REQUEST",
              "reason" -> ""
            )
          )
        )
      )
    }

    "return a structured 400 for constraint violation with missing required field" in {
      val certificateRequestMissingRequiredField = validCertificateRequest.as[JsObject] - "companies"

      assertValidationError(
        testSubscriptionId,
        certificateRequestMissingRequiredField,
        Json.obj(
          "origin"   -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(Json.obj("type" -> "MISSING_REQUIRED_FIELD", "reason" -> "companies"))
          )
        )
      )
    }
  }
}
