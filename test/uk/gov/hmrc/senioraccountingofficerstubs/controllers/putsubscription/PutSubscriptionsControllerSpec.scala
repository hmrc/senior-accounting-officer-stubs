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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers.putsubscription

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
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.{NoneDefaultApiConfiguration, SignupStubConfiguration}
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.SignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.{generateCrn, generateUtr}

import scala.concurrent.Future

class PutSubscriptionsControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach {

  private val testSafeId             = "123"
  private val testUtr                = generateUtr
  private val testSubscriptionId     = "1234"
  private val testLongSubscriptionId = "1234567890123456"

  private val authHeader = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"

  private val validSubscriptionRequest = Json.obj(
    "etmpSafeId"       -> testSafeId,
    "nominatedCompany" -> Json.obj(
      "name" -> "Acme Manufacturing Ltd",
      "utr"  -> testUtr,
      "crn"  -> generateCrn
    ),
    "contacts" -> Json.arr(
      Json.obj("name" -> "Jane Doe", "email" -> "jane.doe@example.com", "status" -> "active", "language" -> "en-gb")
    )
  )

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  private def fakeSubscriptionsPUTRequest(id: String, payload: JsValue) =
    FakeRequest("PUT", s"/dapm/subscriptions/$id")
      .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
      .withTextBody(payload.toString())

  val mockRepository: SignupConfigRepository = mock[SignupConfigRepository]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[SignupConfigRepository].toInstance(mockRepository))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
    when(mockRepository.get(any())).thenReturn(Future.successful(None))
  }

  "PUT /dapm/subscriptions" should {
    "return 201 for a valid request payload" in {
      val result = routeResult(fakeSubscriptionsPUTRequest(testSubscriptionId, validSubscriptionRequest))
      status(result) shouldBe Status.CREATED
    }

    "return 400 for a subscriptionId that is more than 15 characters long" in {
      val result = routeResult(fakeSubscriptionsPUTRequest(testLongSubscriptionId, validSubscriptionRequest))
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj(
          "failures" -> Json.arr(
            Json.obj(
              "type"   -> "validation.request.parameter.schema.maxLength",
              "reason" -> "subscriptionId must be at most 15 characters long"
            )
          )
        )
      )
    }

    "return a 404 for a configured safeId" in {
      when(mockRepository.get(meq(testUtr)))
        .thenReturn(
          Future.successful(
            Some(
              SignupStubConfiguration(
                utr = testUtr,
                putDpsSubscription = Some(NoneDefaultApiConfiguration(status = Status.NOT_FOUND))
              )
            )
          )
        )
      val result = routeResult(fakeSubscriptionsPUTRequest(testSubscriptionId, validSubscriptionRequest))
      status(result) shouldBe Status.NOT_FOUND
    }

    "return a structured 400 for constraint violation with malformed request when JSON syntax is incorrect" in {
      val fakeRequest = FakeRequest("PUT", s"/dapm/subscriptions/$testUtr")
        .withHeaders(CONTENT_TYPE -> "application/json", AUTHORIZATION -> authHeader)
        .withTextBody("""{"subscription":""")

      val result = routeResult(fakeRequest)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj(
          "failures" -> Json.arr(
            Json.obj(
              "type" -> "validation.request.body.schema.invalidJson",
              "reason" -> "ERROR - Unable to parse JSON - Unexpected end-of-input within/between Object entries\n\t at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 17].: []"
            )
          )
        )
      )
    }

    "return a structured 400 for constraint violation with invalid data type when the request is an invalid JSON object" in {
      val invalidSubscriptionRequest: JsValue = Json.obj(
        "etmpSafeId" -> Json.arr("Invalid")
      )

      val result = routeResult(fakeSubscriptionsPUTRequest(testSubscriptionId, invalidSubscriptionRequest))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj(
          "failures" -> Json.arr(
            Json.obj(
              "type"   -> "validation.request.body.schema.type",
              "reason" -> "/etmpSafeId array found, string expected"
            ),
            Json.obj(
              "type"   -> "validation.request.body.schema.required",
              "reason" -> "ERROR - required property 'contacts' not found: []"
            ),
            Json.obj(
              "type"   -> "validation.request.body.schema.required",
              "reason" -> "ERROR - required property 'nominatedCompany' not found: []"
            )
          )
        )
      )
    }

    "return a structured 400 for constraint violation with invalid data type when there is an additional json property" in {
      val additionalProperty: JsObject     = Json.obj("extraProperty" -> "I shouldn't be here")
      val subscriptionRequestExtraProperty = validSubscriptionRequest.as[JsObject] ++ additionalProperty

      val result = routeResult(fakeSubscriptionsPUTRequest(testSubscriptionId, subscriptionRequestExtraProperty))
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj(
          "failures" -> Json.arr(
            Json.obj(
              "type" -> "validation.request.body.schema.additionalProperties",
              "reason" -> "ERROR - property 'extraProperty' is not defined in the schema and the schema does not allow additional properties: []"
            )
          )
        )
      )
    }

    "return a structured 400 for constraint violation with missing required field" in {
      val subscriptionRequestMissingRequiredField = validSubscriptionRequest.as[JsObject] - "etmpSafeId"

      val result =
        routeResult(fakeSubscriptionsPUTRequest(testSubscriptionId, subscriptionRequestMissingRequiredField))
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "origin"   -> "HIP",
        "response" -> Json.obj(
          "failures" -> Json.arr(
            Json.obj(
              "type"   -> "validation.request.body.schema.required",
              "reason" -> "ERROR - required property 'etmpSafeId' not found: []"
            )
          )
        )
      )
    }
  }
}
