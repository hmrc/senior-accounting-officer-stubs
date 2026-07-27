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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers.crmm

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.MimeTypes
import play.api.http.Status
import play.api.inject.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.*
import play.api.mvc.AnyContentAsText
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.crmm.CrmmControllerSpec.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.{NoneDefaultApiConfiguration, SignupStubConfiguration}
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.SignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.{generateCrn, generateUtr}

import scala.concurrent.Future

import java.util.UUID

class CrmmControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach {
  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] = {
    route(app, request) match
      case Some(value) => value
      case None        => fail("Expected route to be defined")
  }

  private val mockRepository = mock[SignupConfigRepository]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[SignupConfigRepository].toInstance(mockRepository))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
    when(mockRepository.get(any())).thenReturn(Future.successful(None))
  }

  "retrieveCustomer" must {

    "return code 400 for a request without sourceSysRef header" in {
      val requestWithoutCorrelationId = FakeRequest("POST", path)
        .withHeaders(headersNoSourceSysRefOrCorrelationId*)
        .withTextBody("")

      val result = routeResult(requestWithoutCorrelationId)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "missing sourceSysRef header"
    }

    "return code 400 for a request without correlationId header" in {
      val requestWithoutCorrelationId = FakeRequest("POST", path)
        .withHeaders(headersNoCorrelationId*)
        .withTextBody("")

      val result = routeResult(requestWithoutCorrelationId)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "missing correlationId header"
    }

    "return structured error message for a request with a companyRegistrationNumber with an invalid format" in {
      val requestBody = """{"companyRegistrationNumber": "|||"}"""

      val requestWithoutCorrelationId = FakeRequest("POST", path)
        .withHeaders(validHeaders*)
        .withTextBody(requestBody)

      val expectedResponse = Json
        .obj(
          "origin"   -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(Json.obj("type" -> "INVALID_FORMAT", "reason" -> "companyRegistrationNumber"))
          )
        )
        .toString

      val result = routeResult(requestWithoutCorrelationId)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedResponse

    }

    "return structured error message for a request with a uniqueTaxReference with an invalid format" in {
      val requestBody = """{"uniqueTaxReference": "|||"}"""

      val requestWithoutCorrelationId = FakeRequest("POST", path)
        .withHeaders(validHeaders*)
        .withTextBody(requestBody)

      val expectedResponse = Json
        .obj(
          "origin"   -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(Json.obj("type" -> "INVALID_FORMAT", "reason" -> "uniqueTaxReference"))
          )
        )
        .toString

      val result = routeResult(requestWithoutCorrelationId)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedResponse
    }

    "return structured error message for a request with no uniqueTaxReference and no companyRegistrationNumber" in {
      val requestBody = "{}"

      val requestWithoutCorrelationId = FakeRequest("POST", path)
        .withHeaders(validHeaders*)
        .withTextBody(requestBody)

      val expectedResponse = Json
        .obj(
          "origin"   -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(
              Json
                .obj("type" -> "MISSING_REQUIRED_FIELD", "reason" -> "companyRegistrationNumber or uniqueTaxReference")
            )
          )
        )
        .toString

      val result = routeResult(requestWithoutCorrelationId)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedResponse
    }
  }

  "return a 200 response with valid headers and request body" in {
    val requestBody = s"""{"uniqueTaxReference": "$generateUtr", "companyRegistrationNumber": "$generateCrn"}"""

    val request = FakeRequest("POST", path)
      .withHeaders(validHeaders*)
      .withTextBody(requestBody)

    val expectedResponseRegex = """\{"customerId":".+","existingCustomer":true,"status":"Success"\}"""

    val result = routeResult(request)

    status(result) shouldBe Status.OK
    contentAsString(result) should fullyMatch regex expectedResponseRegex
  }

  "return a response with a configured status code with valid headers and request body" in {
    val requestBody = s"""{"uniqueTaxReference": "$generateUtr", "companyRegistrationNumber": "$generateCrn"}"""

    when(mockRepository.get(meq(correlationId))).thenReturn(
      Future.successful(
        Some(
          SignupStubConfiguration(
            utr = generateUtr,
            postCrmmRetrieveCustomer = Some(NoneDefaultApiConfiguration(status = Status.IM_A_TEAPOT))
          )
        )
      )
    )

    val request = FakeRequest("POST", path)
      .withHeaders(validHeaders*)
      .withTextBody(requestBody)

    val expectedResponseRegex = """\{"customerId":".+","existingCustomer":true,"status":"Success"\}"""

    val result = routeResult(request)

    status(result) shouldBe Status.IM_A_TEAPOT
    contentAsString(result) should fullyMatch regex expectedResponseRegex
  }

  "return a response with a configured status code and request body with valid headers and request body" in {
    val expectedResponse = "random string response"

    val requestBody = s"""{"uniqueTaxReference": "$generateUtr", "companyRegistrationNumber": "$generateCrn"}"""

    when(mockRepository.get(meq(correlationId))).thenReturn(
      Future.successful(
        Some(
          SignupStubConfiguration(
            utr = generateUtr,
            postCrmmRetrieveCustomer = Some(
              NoneDefaultApiConfiguration(status = Status.IM_A_TEAPOT, defaultBodyOverride = Some(expectedResponse))
            )
          )
        )
      )
    )

    val request = FakeRequest("POST", path)
      .withHeaders(validHeaders*)
      .withTextBody(requestBody)

    val result = routeResult(request)

    status(result) shouldBe Status.IM_A_TEAPOT
    contentAsString(result) shouldBe expectedResponse
  }
}

object CrmmControllerSpec {
  val path = "/compliance/civil-investigation-and-avoidance/api/customer/v1/retrievecustomer"

  val authHeader = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"

  val headersNoSourceSysRefOrCorrelationId: Seq[(String, String)] = Seq(
    CONTENT_TYPE  -> MimeTypes.JSON,
    AUTHORIZATION -> authHeader
  )

  val headersNoCorrelationId: Seq[(String, String)] =
    headersNoSourceSysRefOrCorrelationId.concat(Seq("sourceSysRef" -> "something"))

  val correlationId: String = UUID.randomUUID().toString

  val validHeaders: Seq[(String, String)] = headersNoCorrelationId.concat(Seq("correlationId" -> correlationId))
}
