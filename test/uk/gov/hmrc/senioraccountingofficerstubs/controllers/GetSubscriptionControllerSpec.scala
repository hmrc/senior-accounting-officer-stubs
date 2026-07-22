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
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.getsubscription.Contact
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.*
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.PostSignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.*

import scala.concurrent.Future

class GetSubscriptionControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with MockitoSugar {

  private val authHeader         = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val testSubscriptionId = "123"

  private def routeResult(request: FakeRequest[AnyContentAsEmpty.type]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  private def fakeGetSubscriptionRequest(id: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("GET", s"/iv_subscriptions/$id")
      .withHeaders(AUTHORIZATION -> authHeader)

  private val mockRepository = mock[PostSignupConfigRepository]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[PostSignupConfigRepository].toInstance(mockRepository))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
    when(mockRepository.get(any())).thenReturn(Future.successful(None))
  }

  "GET /iv_subscriptions/:saoSubscriptionId" when {
    "PostSignupConfigRepository does not return a config for this endpoint" must {
      "return 200 with a default response payload" in {
        val result = routeResult(fakeGetSubscriptionRequest(testSubscriptionId))

        status(result) mustBe Status.OK
        contentAsString(
          result
        ) must fullyMatch regex """^\{"etmpSafeId":".+","contacts":\[\{"name":".+","email":".+","language":".+","status":".+"\},\{"name":".+","email":".+","language":".+","status":".+"\}\],"nominatedCompany":\{"crn":".+","name":".+","utr":".+"\}\}$"""
      }
    }

    "PostSignupConfigRepository returns a GetSubscriptionOnlyConfig config" when {
      "the configuration is solely a 204 status code" must {
        "return 204 status code and no body" in {
          val testConfiguredStatus = Status.NO_CONTENT
          when(mockRepository.get(meq(testSubscriptionId))).thenReturn(
            Future.successful(
              Some(
                PostSignupStubConfiguration(
                  subscriptionId = testSubscriptionId,
                  getSubscriptionAndPostRetrieveCustomerId =
                    Some(GetSubscriptionOnlyConfig(status = testConfiguredStatus))
                )
              )
            )
          )

          val result = routeResult(fakeGetSubscriptionRequest(testSubscriptionId))
          status(result) mustBe testConfiguredStatus
          contentAsString(
            result
          ) mustBe ""
        }
      }

      "the configuration is solely a none-204 status code" must {
        "return the configured none-204 status code and the default body" in {
          val testConfiguredStatus = Status.NOT_FOUND
          when(mockRepository.get(meq(testSubscriptionId))).thenReturn(
            Future.successful(
              Some(
                PostSignupStubConfiguration(
                  subscriptionId = testSubscriptionId,
                  getSubscriptionAndPostRetrieveCustomerId =
                    Some(GetSubscriptionOnlyConfig(status = testConfiguredStatus))
                )
              )
            )
          )

          val result = routeResult(fakeGetSubscriptionRequest(testSubscriptionId))
          status(result) mustBe testConfiguredStatus
          contentAsString(
            result
          ) must fullyMatch regex """^\{"etmpSafeId":".+","contacts":\[\{"name":".+","email":".+","language":".+","status":".+"\},\{"name":".+","email":".+","language":".+","status":".+"\}\],"nominatedCompany":\{"crn":".+","name":".+","utr":".+"\}\}$"""
        }
      }

      "the configuration is both a status code and a body" must {
        "return the configured status code and the configured body" in {
          val testConfiguredStatus = Status.IM_A_TEAPOT

          val testConfigBody = "result"
          when(mockRepository.get(meq(testSubscriptionId))).thenReturn(
            Future.successful(
              Some(
                PostSignupStubConfiguration(
                  subscriptionId = testSubscriptionId,
                  getSubscriptionAndPostRetrieveCustomerId = Some(
                    GetSubscriptionOnlyConfig(
                      status = testConfiguredStatus,
                      defaultBodyOverride = Some(testConfigBody)
                    )
                  )
                )
              )
            )
          )

          val result = routeResult(fakeGetSubscriptionRequest(testSubscriptionId))
          status(result) mustBe testConfiguredStatus
          contentAsString(result) mustBe testConfigBody
        }
      }
    }

    "PostSignupConfigRepository returns a PostRetrieveCustomerIdConfig config" must {
      "return 200 and the configured body" in {
        val testUtr = generateUtr
        val testCrn = generateCrn

        when(mockRepository.get(meq(testSubscriptionId))).thenReturn(
          Future.successful(
            Some(
              PostSignupStubConfiguration(
                subscriptionId = testSubscriptionId,
                getSubscriptionAndPostRetrieveCustomerId = Some(
                  PostRetrieveCustomerIdConfig(
                    getSubscription = GetSubscriptionConfig(
                      utr = testUtr,
                      crn = Some(testCrn),
                      name = Some("Tester Name"),
                      contacts = List(
                        Contact(
                          name = Some("Test Contact Name"),
                          email = Some("Some email"),
                          language = Some("Some language"),
                          status = Some("Some status")
                        )
                      )
                    ),
                    status = -1,
                    defaultBodyOverride = None
                  )
                )
              )
            )
          )
        )

        val result = routeResult(fakeGetSubscriptionRequest(testSubscriptionId))
        status(result) mustBe 200
        contentAsJson(result) mustBe Json.parse(
          s"""{
             |  "contacts":[
             |    {"name":"Test Contact Name","email":"Some email","language":"Some language","status":"Some status"}
             |  ],
             |  "nominatedCompany":{"crn":"$testCrn","name":"Tester Name","utr":"$testUtr"}
             |}""".stripMargin
        )
      }
    }

  }

}
