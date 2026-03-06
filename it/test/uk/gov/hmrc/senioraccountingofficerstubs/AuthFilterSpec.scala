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

package uk.gov.hmrc.senioraccountingofficerstubs

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.senioraccountingofficerstubs.config.AppConfig

class AuthFilterSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite {

  private val wsClient = app.injector.instanceOf[WSClient]
  private val appConfig = app.injector.instanceOf[AppConfig]
  private val baseUrl  = s"http://localhost:$port"
  private val knownSubscriptionId = "123"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .build()

  "Auth filter - Contact-details" should {
    "respond with 401 status when no authorisation header is provided" in {
      val response =
        wsClient
          .url(s"$baseUrl/contact-details/$knownSubscriptionId")
          .get()
          .futureValue

      response.status shouldBe 401
    }

    "respond with 401 status when an invalid authorisation header is provided" in {
      val response =
        wsClient
          .url(s"$baseUrl/contact-details/$knownSubscriptionId")
          .withHttpHeaders(("Authorization","testHeader"))
          .get()
          .futureValue

      response.status shouldBe 401
    }

    "respond with 200 status when an authorisation header is provided" in {

      val base64String = "Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
      val decodedAuth = java.util.Base64.getDecoder.decode(base64String)
      val stringAuth: String = new String(decodedAuth)

      stringAuth shouldBe s"${appConfig.clientId}:${appConfig.clientSecret}"

      val response =
        wsClient
          .url(s"$baseUrl/contact-details/$knownSubscriptionId")
          .withHttpHeaders(("Authorization", s"Basic $base64String"))
          .get()
          .futureValue

      response.status shouldBe 200
    }
  }

  "Auth filter - Subscriptions" should {
    "respond with 401 status when no authorisation header is provided" in {
      val response =
        wsClient
          .url(s"$baseUrl/subscriptions")
          .put(Json.obj("subscription" -> Json.obj("name" -> "Test Data Ltd")))
          .futureValue

      response.status shouldBe 401
    }

    "respond with 401 status when an invalid authorisation header is provided" in {
      val response =
        wsClient
          .url(s"$baseUrl/subscriptions")
          .withHttpHeaders(("Authorization","testHeader"))
          .put(Json.obj("subscription" -> Json.obj("name" -> "Test Data Ltd")))
          .futureValue

      response.status shouldBe 401
    }

    "respond with 200 status when an authorisation header is provided" in {

      val base64String = "Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
      val decodedAuth = java.util.Base64.getDecoder.decode(base64String)
      val stringAuth: String = new String(decodedAuth)

      stringAuth shouldBe s"${appConfig.clientId}:${appConfig.clientSecret}"

      val response =
        wsClient
          .url(s"$baseUrl/subscriptions")
          .withHttpHeaders(("Authorization", s"Basic $base64String"))
          .put(Json.obj("subscription" -> Json.obj("name" -> "Test Data Ltd")))
          .futureValue

      response.status shouldBe 200
    }
  }
}
