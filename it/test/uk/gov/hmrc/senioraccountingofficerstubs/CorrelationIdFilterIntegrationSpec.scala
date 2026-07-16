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
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.*
import uk.gov.hmrc.senioraccountingofficerstubs.config.AppConfig
import CorrelationIdFilterIntegrationSpec.*
import play.api.http.Status.*
import play.api.mvc.ControllerComponents
import play.api.mvc.Results.*

import java.util.UUID

class CorrelationIdFilterIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite {

  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl  = s"http://localhost:$port"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "play.filters.enabled" -> Seq("uk.gov.hmrc.senioraccountingofficerstubs.filters.CorrelationIdFilter")
      )
      .appRoutes { app =>
        val controllerComponents = app.injector.instanceOf[ControllerComponents]
        val action               = controllerComponents.actionBuilder
        { case ("GET", testUrl) =>
          action { request =>
            Ok(testSuccessBody)
          }
        }
      }
      .build()

  def targetUrl = s"$baseUrl$testPath"

  "CorrelationId filter" must {
    "pass when the request does not contain a correlationId header" in {
      val response =
        wsClient
          .url(targetUrl)
          .get()
          .futureValue

      response.status mustBe OK
      response.body[String] mustBe testSuccessBody
    }

    "pass when the request contains a valid correlationId header" in {
      val response =
        wsClient
          .url(targetUrl)
          .withHttpHeaders("correlationId" -> UUID.randomUUID().toString)
          .get()
          .futureValue

      response.status mustBe OK
      response.body[String] mustBe testSuccessBody
    }

    "return 400 when the request contains an invalid correlationId header" in {
      val response =
        wsClient
          .url(targetUrl)
          .withHttpHeaders("correlationId" -> "not a uuid")
          .get()
          .futureValue

      response.status mustBe BAD_REQUEST
      response.body[JsValue] mustBe
        Json.parse("""
          |{
          |   "origin":"HIP",
          |   "response":{
          |     "failures":[
          |       {
          |         "type":"header.CorrelationId",
          |         "reason":"The request parameter header.CorrelationId failed validation."
          |       }
          |     ]
          |  }
          |}
          |""".stripMargin)
    }
  }

}

object CorrelationIdFilterIntegrationSpec {
  val testPath        = "/test-correlationid-filter"
  val testSuccessBody = "Filter Passed Successfully"
}
