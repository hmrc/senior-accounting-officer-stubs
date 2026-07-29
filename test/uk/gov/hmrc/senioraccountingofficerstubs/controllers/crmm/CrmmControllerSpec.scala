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
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
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
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.*
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.PostSignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.{generateCrn, generateUtr}

import scala.concurrent.Future

import java.util.UUID

class CrmmControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach {
  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] = {
    route(app, request) match
      case Some(value) => value
      case None        => fail("Expected route to be defined")
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

  "POST /compliance/civil-investigation-and-avoidance/api/customer/v1/retrievecustomer" - {
    "sourceSysRef header is not sent" - {
      "return a 400 response" in {
        val requestWithoutCorrelationId = FakeRequest("POST", path)
          .withHeaders(headersNoSourceSysRefOrCorrelationId*)
          .withTextBody("")

        val expectedResponse = Json.parse("""{
                                            |  "origin": "HIP",
                                            |  "response": {
                                            |    "failures" : [
                                            |      {
                                            |        "type": "MISSING_REQUIRED_FIELD",
                                            |        "reason": "headers.sourceSysRef"
                                            |      }
                                            |    ]
                                            |  }
                                            |}""".stripMargin)

        val result = routeResult(requestWithoutCorrelationId)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsJson(result) shouldBe expectedResponse
      }
    }

    "correlationId header is not sent" - {
      "return a 400 response" in {
        val requestWithoutCorrelationId = FakeRequest("POST", path)
          .withHeaders(headersNoCorrelationId*)
          .withTextBody("")

        val expectedResponse = Json.parse("""{
                                            |  "origin": "HIP",
                                            |  "response": {
                                            |    "failures": [
                                            |      {
                                            |        "type": "MISSING_REQUIRED_FIELD",
                                            |        "reason": "headers.correlationId"
                                            |      }
                                            |    ]
                                            |  }
                                            |}""".stripMargin)

        val result = routeResult(requestWithoutCorrelationId)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsJson(result) shouldBe expectedResponse
      }
    }

    "a request with an invalid companyRegistrationNumber is sent" - {
      "return structured error message" in {
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

        val result = routeResult(requestWithoutCorrelationId)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsJson(result) shouldBe expectedResponse
      }
    }

    "a request with an additional field is sent" - {
      "return structured error message" in {
        val requestBody = s"""{"$unkownProperty": "Firstname Lastname"}"""

        val requestWithoutCorrelationId = FakeRequest("POST", path)
          .withHeaders(validHeaders*)
          .withTextBody(requestBody)

        val expectedResponse = Json
          .obj(
            "origin"   -> "HIP",
            "response" -> Json.obj(
              "failures" -> Json.arr(Json.obj("type" -> "INVALID_DATA_TYPE", "reason" -> unkownProperty))
            )
          )

        val result = routeResult(requestWithoutCorrelationId)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsJson(result) shouldBe expectedResponse
      }
    }

    "a request with an invalid uniqueTaxReference is sent" - {
      "return structured error message" in {
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

        val result = routeResult(requestWithoutCorrelationId)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsJson(result) shouldBe expectedResponse
      }
    }

    "a request without an uniqueTaxReference or companyRegistrationNumber is sent" - {
      "return structured error message" in {
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
                  .obj(
                    "type"   -> "MISSING_REQUIRED_FIELD",
                    "reason" -> "companyRegistrationNumber or uniqueTaxReference"
                  )
              )
            )
          )

        val result = routeResult(requestWithoutCorrelationId)

        status(result) shouldBe Status.BAD_REQUEST
        contentAsJson(result) shouldBe expectedResponse
      }
    }

    "PostSignupConfigRepository does not return a config for this endpoint" - {
      "return 200 response with a default response payload" in {
        val requestBody = s"""{"uniqueTaxReference": "$utr", "companyRegistrationNumber": "$crn"}"""

        when(mockRepository.getByCrnAndUtr(crn = any(), utr = any())).thenReturn(
          Future.successful(None)
        )

        val request = FakeRequest("POST", path)
          .withHeaders(validHeaders*)
          .withTextBody(requestBody)

        val expectedResponseRegex = """\{"customerId":".+","existingCustomer":true,"status":"Success"\}"""

        val result = routeResult(request)

        status(result) shouldBe Status.OK
        contentAsString(result) should fullyMatch regex expectedResponseRegex
      }
    }

    "PostSignupConfigRepository returns a GetSubscriptionConfig config" - {
      "the configuration is solely a 418 status code" - {
        "return the configured status code and the default body" in {
          val requestBody = s"""{"uniqueTaxReference": "$utr", "companyRegistrationNumber": "$crn"}"""

          when(mockRepository.getByCrnAndUtr(crn = meq(crn), utr = meq(utr))).thenReturn(
            Future.successful(
              Some(
                PostSignupStubConfiguration(
                  subscriptionId = "sub id",
                  getSubscriptionAndPostRetrieveCustomerId = Some(
                    PostRetrieveCustomerIdConfig(
                      getSubscription = GetSubscriptionConfig(utr = utr, crn = Some(crn)),
                      status = Status.IM_A_TEAPOT,
                      defaultBodyOverride = None
                    )
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
          contentAsString(
            result
          ) should fullyMatch regex """\{"customerId":".+","existingCustomer":true,"status":"Success"\}"""
        }
      }

      "the configuration is both a status code and a body" - {
        "return the configured status code and the configured body" in {
          val expectedResponse = "random string response"

          val requestBody = s"""{"uniqueTaxReference": "$utr", "companyRegistrationNumber": "$crn"}"""

          when(mockRepository.getByCrnAndUtr(crn = meq(crn), utr = meq(utr))).thenReturn(
            Future.successful(
              Some(
                PostSignupStubConfiguration(
                  subscriptionId = "sub id",
                  getSubscriptionAndPostRetrieveCustomerId = Some(
                    PostRetrieveCustomerIdConfig(
                      getSubscription = GetSubscriptionConfig(utr = utr, crn = Some(crn)),
                      status = Status.IM_A_TEAPOT,
                      defaultBodyOverride = Some(expectedResponse)
                    )
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
    }
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
  val crn                   = generateCrn
  val utr                   = generateUtr

  val validHeaders: Seq[(String, String)] = headersNoCorrelationId.concat(Seq("correlationId" -> correlationId))

  val unkownProperty = "saoMagicField"
}
