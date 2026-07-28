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

import org.mockito.ArgumentMatchers.any
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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsJson, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficerstubs.connectors.FileUploadSdesStubConnector

import scala.concurrent.Future

class SdesNotificationControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with MockitoSugar {

  private val mockConnector = mock[FileUploadSdesStubConnector]

  private val validPayload = Json.obj(
    "informationType"   -> "DSAO",
    "fileName"          -> "20260728_NOT0000000001_SAO_Notification_OFFICIAL_SENSITIVE.zip",
    "objectStorePath"   -> "/senior-accounting-officer/NOT0000000001/file.zip",
    "checksum"          -> "checksum",
    "checksumAlgorithm" -> "md5",
    "contentLength"     -> 123
  )

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[FileUploadSdesStubConnector].toInstance(mockConnector))
    .configure(
      "sdes-proxy-stub.xClientId"       -> "senior-accounting-officer",
      "sdes-proxy-stub.informationType" -> "DSAO"
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector)
  }

  private def routeResult(request: FakeRequest[AnyContentAsJson]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  private def request(payload: JsValue = validPayload, clientId: String = "senior-accounting-officer") =
    FakeRequest("POST", "/notification/fileready")
      .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, "X-Client-ID" -> clientId)
      .withJsonBody(payload)

  "POST /notification/fileready" should {
    "forward a valid file ready notification to FILE_UPLOAD_SDES_STUB" in {
      when(mockConnector.notifyFileReady(any[JsValue])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(HttpResponse(Status.ACCEPTED, """{"accepted":true}""")))

      val result = routeResult(request())

      status(result) shouldBe Status.ACCEPTED
      contentAsJson(result) shouldBe Json.obj("accepted" -> true)
      verify(mockConnector).notifyFileReady(any[JsValue])(using any[HeaderCarrier])
    }

    "reject a notification with an invalid X-Client-ID" in {
      val result = routeResult(request(clientId = "wrong-client"))

      status(result) shouldBe Status.FORBIDDEN
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "INVALID_CLIENT_ID",
        "message" -> "The supplied X-Client-ID is not supported"
      )
      verifyNoInteractions(mockConnector)
    }

    "reject a notification with an invalid informationType" in {
      val result = routeResult(request(payload = validPayload ++ Json.obj("informationType" -> "wrong-type")))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "INVALID_INFORMATION_TYPE",
        "message" -> "The supplied informationType is not supported"
      )
      verifyNoInteractions(mockConnector)
    }

    "surface the FILE_UPLOAD_SDES_STUB response status and body" in {
      when(mockConnector.notifyFileReady(any[JsValue])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(HttpResponse(Status.INTERNAL_SERVER_ERROR, """{"failed":true}""")))

      val result = routeResult(request())

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj("failed" -> true)
    }
  }
}
