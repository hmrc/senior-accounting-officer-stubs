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

package uk.gov.hmrc.senioraccountingofficerstubs.controllers.testOnly

import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{MimeTypes, Status, Writeable}
import play.api.inject.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.senioraccountingofficerstubs.controllers.testOnly.ConfigureSignupControllerSpec.*
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.*
import uk.gov.hmrc.senioraccountingofficerstubs.repositories.SignupConfigRepository
import uk.gov.hmrc.senioraccountingofficerstubs.utils.TestDataGenerator.generateUtr

import scala.concurrent.Future

import java.time.Instant
import java.time.temporal.ChronoUnit

class ConfigureSignupControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with MockitoSugar {

  private val mockRepository = mock[SignupConfigRepository]
  private val instant        = Instant.now.truncatedTo(ChronoUnit.MILLIS)

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[SignupConfigRepository].toInstance(mockRepository)
    )
    .configure("application.router" -> "testOnlyDoNotUseInAppConf.Routes")
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
    when(mockRepository.set(any())).thenReturn(Future.successful(true))
  }

  private def routeResult[A](request: FakeRequest[A])(using Writeable[A]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  private def fakeRequest(payload: String) =
    FakeRequest("POST", "/test-only/signup/config")
      .withBody(payload)
      .withHeaders(CONTENT_TYPE -> MimeTypes.JSON)

  "ConfigurePostSignupController" must {
    "return 400 for a bad request" in {
      val result = routeResult(fakeRequest(""))

      status(result) mustBe Status.BAD_REQUEST
    }

    "return 200 for a valid request and persist it into Mongo" in {
      val expectedConfig = SignupStubConfiguration(
        utr = testUtr,
        postEtmpSubscription = Some(NoneDefaultApiConfiguration(status = 500)),
        putDpsSubscription = Some(NoneDefaultApiConfiguration(status = 400)),
        lastUpdated = instant
      )

      val expectedConfigAsString =
        s"""
        |{
        |  "utr": "$testUtr",
        |  "postEtmpSubscription": {
        |    "status": 500
        |  },
        |  "putDpsSubscription": {
        |    "status": 400
        |  }
        |}
        |""".stripMargin

      val result = routeResult(fakeRequest(expectedConfigAsString))

      status(result) mustBe Status.OK
      verify(mockRepository, times(1)).set(
        argThat((capturedConfig: SignupStubConfiguration) =>
          capturedConfig.copy(lastUpdated = instant) == expectedConfig
        )
      )
    }
  }
}

object ConfigureSignupControllerSpec {
  val testUtr: String = generateUtr
}
