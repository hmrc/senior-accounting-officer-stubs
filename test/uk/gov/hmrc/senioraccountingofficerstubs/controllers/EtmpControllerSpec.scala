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

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalactic.Prettifier.default
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
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.{NoneDefaultApiConfiguration, SignupStubConfiguration}
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.SignupConfigRepository

import scala.concurrent.Future
import scala.util.Random

import java.util.UUID

class EtmpControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach {

  private val authHeader = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"

  private val testIdNumber = f"${Random.nextInt(100000)}%010d"

  private val validEtmpRequest: JsValue = Json.obj(
    "idType"   -> "UTR",
    "idNumber" -> testIdNumber
  )

  private val mockRepository: SignupConfigRepository = mock[SignupConfigRepository]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[SignupConfigRepository].toInstance(mockRepository))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
    when(mockRepository.get(any())).thenReturn(Future.successful(None))
  }

  private def stubEtmpStatus(status: Int, body: Option[String] = None): Unit =
    when(mockRepository.get(meq(testIdNumber))).thenReturn(
      Future.successful(
        Some(
          SignupStubConfiguration(
            utr = testIdNumber,
            postEtmpSubscription = Some(NoneDefaultApiConfiguration(status, body))
          )
        )
      )
    )

  private val xTransmittingSystem = ("X-Transmitting-System" -> "HIP")
  private val xOriginatingSystem  = ("X-Originating-System"  -> "MDTP")
  private val correlationId       = ("correlationId"         -> UUID.randomUUID().toString)
  private val xReceiptDate        = ("X-Receipt-Date"        -> "2026-05-05T12:05:45Z")

  def apiUri = "/etmp/RESTAdapter/dsao/subscription"

  private def fakeEtmpPOSTRequest(payload: JsValue) = {
    FakeRequest("POST", apiUri).withHeaders(
      CONTENT_TYPE   -> MimeTypes.JSON,
      (AUTHORIZATION -> authHeader),
      xTransmittingSystem,
      xOriginatingSystem,
      correlationId,
      xReceiptDate
    )
  }
    .withTextBody(payload.toString)

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match
      case Some(value) => value
      case None        => fail("Expected route to be defined")

  "POST /etmp/RESTAdapter/dsao/subscription" should {
    "return code 201, correlationId header, and an Etmp success response for an idNumber" in {
      val request          = fakeEtmpPOSTRequest(validEtmpRequest)
      val correlationId    = request.headers.get("correlationId").get
      val result           = routeResult(request)
      val expectedResponse =
        """^\{"success":\{"processingDate":"[0-9]{4}-([0][1-9]|[1][0-2])-([0][1-9]|[1-2][0-9]|[3][0-1])T([0-1][0-9]|[2][0-3]):[0-5][0-9]:[0-5][0-9]Z","dsaoIdNumber":"XB[0-9]{1,15}"\}}$"""

      status(result) shouldBe Status.CREATED
      contentAsString(result) should fullyMatch regex expectedResponse
      header("correlationId", result) shouldBe Some(correlationId)
    }

    "return code 400 for a request without a required header, correlationId" in {
      val requestWithoutCorrelationId = FakeRequest("POST", apiUri)
        .withHeaders(
          CONTENT_TYPE  -> MimeTypes.JSON,
          AUTHORIZATION -> authHeader,
          xTransmittingSystem,
          xOriginatingSystem,
          xReceiptDate
        )
        .withTextBody(validEtmpRequest.toString)

      val result = routeResult(requestWithoutCorrelationId)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe "missing correlationId header"
    }

    "return code 400 for a request where a required header is invalid" in {
      val invalidXTransmittingSystemHeader = xTransmittingSystem._1 -> "Test"
      val requestWithInvalidHeader         = FakeRequest("POST", apiUri)
        .withHeaders(
          CONTENT_TYPE  -> MimeTypes.JSON,
          AUTHORIZATION -> authHeader,
          invalidXTransmittingSystemHeader,
          xOriginatingSystem,
          xReceiptDate
        )
        .withTextBody(validEtmpRequest.toString)

      val result = routeResult(requestWithInvalidHeader)
      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "invalid X-Transmitting-System header"
    }

    "return a structured 400 for a request with invalid enum for idType" in {
      val invalidPayload: JsValue = Json.obj(
        "idNumber" -> f"${Random.nextInt(100000)}%010d",
        "idType"   -> "Test"
      )
      val expectedContent = Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj("failures" -> Json.arr(Json.obj("type" -> "INVALID_ENUM_VALUE", "reason" -> "idType")))
      )
      val result = routeResult(fakeEtmpPOSTRequest(invalidPayload))

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe expectedContent
    }

    "return a structured 400 for when any extra fields are added" in {
      val invalidPayload  = validEtmpRequest.as[JsObject] ++ Json.obj("extra" -> "value")
      val expectedPayload = Json
        .obj(
          "origin"   -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(Json.obj("type" -> "INVALID_DATA_TYPE", "reason" -> "extra"))
          )
        )
        .toString

      val result = routeResult(fakeEtmpPOSTRequest(invalidPayload))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedPayload
    }

    "return a structured 400 for constraint violation with missing required field" in {
      val invalidPayload  = validEtmpRequest.as[JsObject] - "idType"
      val result          = routeResult(fakeEtmpPOSTRequest(invalidPayload))
      val expectedPayload = Json
        .obj(
          "origin"   -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(Json.obj("type" -> "MISSING_REQUIRED_FIELD", "reason" -> "idType"))
          )
        )
        .toString
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedPayload
    }

    "return a structured 400 for a malformed request" in {
      val expectedPayload = Json
        .obj(
          "origin"   -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(Json.obj("type" -> "MALFORMED_REQUEST", "reason" -> ""))
          )
        )
        .toString
      val result = routeResult(fakeEtmpPOSTRequest(validEtmpRequest).withTextBody("("))
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe expectedPayload
    }
  }

  "POST /etmp/RESTAdapter/dsao/subscription with a configured stub response" should {
    val hipFailure = Json
      .obj(
        "origin"   -> "HIP",
        "response" -> Json.obj("failures" -> Json.arr(Json.obj("type" -> "string", "reason" -> "string")))
      )
      .toString

    Seq(
      Status.UNAUTHORIZED,
      Status.FORBIDDEN,
      Status.NOT_FOUND,
      Status.UNPROCESSABLE_ENTITY,
      Status.INTERNAL_SERVER_ERROR,
      Status.SERVICE_UNAVAILABLE
    ).foreach { configuredStatus =>
      s"return the configured $configuredStatus with its body and the correlationId header" in {
        stubEtmpStatus(configuredStatus, Some(hipFailure))

        val request       = fakeEtmpPOSTRequest(validEtmpRequest)
        val correlationId = request.headers.get("correlationId").get
        val result        = routeResult(request)

        status(result) shouldBe configuredStatus
        contentAsString(result) shouldBe hipFailure
        header("correlationId", result) shouldBe Some(correlationId)
      }
    }

    "return 204 with no body when configured with 204" in {
      stubEtmpStatus(Status.NO_CONTENT)

      val result = routeResult(fakeEtmpPOSTRequest(validEtmpRequest))

      status(result) shouldBe Status.NO_CONTENT
      contentAsString(result) shouldBe ""
    }

    "return 201 when the configuration holds no ETMP override" in {
      when(mockRepository.get(meq(testIdNumber))).thenReturn(
        Future.successful(Some(SignupStubConfiguration(utr = testIdNumber)))
      )

      val result = routeResult(fakeEtmpPOSTRequest(validEtmpRequest))

      status(result) shouldBe Status.CREATED
    }

    "reject an invalid request before consulting the configuration" in {
      stubEtmpStatus(Status.SERVICE_UNAVAILABLE)

      val result = routeResult(fakeEtmpPOSTRequest(validEtmpRequest.as[JsObject] - "idType"))

      status(result) shouldBe Status.BAD_REQUEST
    }

    Seq(
      "a missing idNumber"    -> (validEtmpRequest.as[JsObject] - "idNumber"),
      "a non-string idNumber" -> (validEtmpRequest.as[JsObject] ++ Json.obj("idNumber" -> 1234567890)),
      "a top level array"     -> Json.arr(validEtmpRequest)
    ).foreach { case (description, payload) =>
      s"return 400 rather than failing the config lookup for $description" in {
        stubEtmpStatus(Status.SERVICE_UNAVAILABLE)

        status(routeResult(fakeEtmpPOSTRequest(payload))) shouldBe Status.BAD_REQUEST
      }
    }
  }
}
