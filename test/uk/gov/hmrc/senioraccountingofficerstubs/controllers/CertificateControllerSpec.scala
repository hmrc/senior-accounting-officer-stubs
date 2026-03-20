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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.{MimeTypes, Status}
import play.api.libs.json.{JsArray, JsObject}
import play.api.mvc.{AnyContentAsText, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future

class CertificateControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  private val authHeader = "Basic Q2xpZW50SWQ6Q2xpZW50U2VjcmV0"
  private val knownId    = "123"
  private val unknownId  = "567"

  import play.api.libs.json.{JsValue, Json}

  private val validCertificateRequest: JsValue = Json.obj(
    "declaration" -> Json.obj(
      "seniorAccountingOfficer" -> Json.obj(
        "name"  -> "Firstname Lastname",
        "email" -> "Firstname.Lastname@example.com"
      ),
      "proxy" -> Json.obj(
        "name" -> "Firstname Lastname"
      )
    ),
    "companies" -> Json.arr(
      Json.obj(
        "companyName"            -> "Example Ltd",
        "uniqueTaxReference"     -> "1234567890",
        "companyReferenceNumber" -> "AB123456",
        "companyType"            -> "LTD",
        "financialYearEndDate"   -> "2024-12-31",
        "qualifiedRegimes"       -> Json.arr(
          "CORPORATION",
          "VALUE_ADDED"
        ),
        "qualificationStatement" -> "All relevant corporation and VAT obligations have been delayed due to grievance.",
        "seniorAccountingOfficers" -> Json.arr(
          Json.obj(
            "name"      -> "Firstname Lastname",
            "email"     -> "Firstname.Lastname@example.com",
            "startDate" -> "2024-04-01",
            "endDate"   -> "2025-03-31"
          ),
          Json.obj(
            "name"      -> "Secondpersonname Theirlastname",
            "email"     -> "non.empty.email@companyname.com",
            "startDate" -> "2024-12-01",
            "endDate"   -> "2025-12-31"
          )
        )
      ),
      Json.obj(
        "companyName"            -> "Example PLC",
        "uniqueTaxReference"     -> "0987654321",
        "companyReferenceNumber" -> "CD654321",
        "companyType"            -> "PLC",
        "financialYearEndDate"   -> "2024-06-30",
        "isDormant"              -> true,
        "isInAdministration"     -> false,
        "qualifiedRegimes"       -> Json.arr(
          "PAY_AS_YOU_EARN",
          "PETROLEUM_REVENUE"
        ),
        "seniorAccountingOfficers" -> Json.arr(
          Json.obj(
            "name"      -> "Another Officer",
            "startDate" -> "2024-05-01",
            "endDate"   -> "2025-04-30"
          )
        )
      )
    ),
    "additionalInformation" -> "non-empty string"
  )

  private def routeResult(request: FakeRequest[AnyContentAsText]): Future[Result] =
    route(app, request) match {
      case Some(value) => value
      case None        => fail("Expected route to be defined")
    }

  private def fakeCertificatePOSTRequest(id: String, payload: JsValue) =
    FakeRequest("POST", s"/certificate/$id")
      .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
      .withTextBody(payload.toString())

  private def assertValidationError(id: String, payload: JsValue, expectedError: JsValue): Unit = {
    val result = routeResult(fakeCertificatePOSTRequest(id, payload))
    status(result) shouldBe Status.BAD_REQUEST
    contentAsJson(result) shouldBe Json.arr(expectedError)
  }

  "POST /certificate/:saoSubscriptionId" should {
    "return 200 and certificate payload for a known saoSubscriptionId" in {
      val result = routeResult(fakeCertificatePOSTRequest(knownId, validCertificateRequest))

      val testCertificateResponse = Json.obj(
        "id"        -> "NOT0123456789",
        "timestamp" -> "2026-03-01T12:00:14Z"
      )

      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe testCertificateResponse
    }

    "return a 404 for an unknown saoSubscriptionId" in {
      val result = routeResult(fakeCertificatePOSTRequest(unknownId, validCertificateRequest))

      status(result) shouldBe Status.NOT_FOUND
    }

    "return a structured 400 for a request with invalid JSON shape" in {

      val invalidCertificateRequest: JsValue = Json.obj(
        "companies" -> Json.arr("Test")
      )

      val result = routeResult(fakeCertificatePOSTRequest(knownId, invalidCertificateRequest))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj(
          "path"   -> "companies[0]",
          "reason" -> "INVALID_DATA_TYPE"
        ),
        Json.obj(
          "path"   -> "declaration",
          "reason" -> "MISSING_REQUIRED_FIELD"
        )
      )
    }

    "return a structured 400 for constraint violation with malformed request when JSON syntax is incorrect" in {
      val fakePOSTRequest = FakeRequest("POST", s"/certificate/$knownId")
        .withHeaders(CONTENT_TYPE -> MimeTypes.JSON, AUTHORIZATION -> authHeader)
        .withTextBody("""{"companies":["Test"]""")

      val result = routeResult(fakePOSTRequest)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("reason" -> "MALFORMED_REQUEST")
      )
    }

    "return a structured 400 for constraint violation with invalid format" in {
      val certificateRequestInvalidFormat = Json.parse(
        validCertificateRequest
          .toString()
          .replaceFirst(
            "Firstname\\.Lastname@example\\.com",
            "Firstname.Lastname example.com"
          )
      )

      assertValidationError(
        knownId,
        certificateRequestInvalidFormat,
        Json.obj(
          "path"   -> "declaration.seniorAccountingOfficer.email",
          "reason" -> "INVALID_FORMAT"
        )
      )
    }

    "return a structured 400 for constraint violation with cannot be empty" in {
      val certificateRequestCannotBeEmpty = Json.parse(
        validCertificateRequest
          .toString()
          .replaceFirst(
            "Example Ltd",
            ""
          )
      )

      assertValidationError(
        knownId,
        certificateRequestCannotBeEmpty,
        Json.obj(
          "path"   -> "companies[0].companyName",
          "reason" -> "CANNOT_BE_EMPTY"
        )
      )
    }

    "return a structured 400 for constraint violation with invalid data type when int is present instead of string" in {
      val companies    = (validCertificateRequest \ "companies").as[JsArray].value
      val firstCompany = companies.head.as[JsObject] ++ Json.obj(
        "uniqueTaxReference" -> 123
      )

      val certificateRequestInvalidDataType =
        validCertificateRequest.as[JsObject] ++ Json.obj(
          "companies" -> JsArray(companies.updated(0, firstCompany))
        )

      assertValidationError(
        knownId,
        certificateRequestInvalidDataType,
        Json.obj(
          "path"   -> "companies[0].uniqueTaxReference",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for constraint violation with invalid data type when there is an additional json property" in {
      val additionalProperty: JsObject    = Json.obj("extraProperty" -> "I shouldn't be here")
      val certificateRequestExtraProperty = validCertificateRequest.as[JsObject] ++ additionalProperty

      assertValidationError(
        knownId,
        certificateRequestExtraProperty,
        Json.obj(
          "path"   -> "extraProperty",
          "reason" -> "INVALID_DATA_TYPE"
        )
      )
    }

    "return a structured 400 for constraint violation with array min items not met" in {
      val certificateRequestArrayMinItemsNotMet: JsValue = Json.obj(
        "declaration" -> Json.obj(
          "seniorAccountingOfficer" -> Json.obj(
            "name"  -> "Firstname Lastname",
            "email" -> "Firstname.Lastname@example.com"
          )
        ),
        "companies"             -> Json.arr(),
        "additionalInformation" -> "non-empty string"
      )

      assertValidationError(
        knownId,
        certificateRequestArrayMinItemsNotMet,
        Json.obj(
          "path"   -> "companies",
          "reason" -> "ARRAY_MIN_ITEMS_NOT_MET"
        )
      )
    }

    "return a structured 400 for constraint violation with length out of bounds" in {
      val certificateRequestLengthOutOfBounds = Json.parse(
        validCertificateRequest
          .toString()
          .replaceFirst(
            "non-empty string",
            "non-empty string " * 300
          )
      )

      assertValidationError(
        knownId,
        certificateRequestLengthOutOfBounds,
        Json.obj(
          "path"   -> "additionalInformation",
          "reason" -> "LENGTH_OUT_OF_BOUNDS"
        )
      )
    }

    "return a structured 400 for constraint violation with invalid enum" in {
      val certificateRequestInvalidEnum = Json.parse(
        validCertificateRequest
          .toString()
          .replaceFirst(
            "LTD",
            "LDX"
          )
      )

      assertValidationError(
        knownId,
        certificateRequestInvalidEnum,
        Json.obj(
          "path"   -> "companies[0].companyType",
          "reason" -> "INVALID_ENUM_VALUE"
        )
      )
    }

    "return a structured 400 for constraint violation with missing required field" in {
      val certificateRequestMissingRequiredField = validCertificateRequest.as[JsObject] - "companies"

      assertValidationError(
        knownId,
        certificateRequestMissingRequiredField,
        Json.obj(
          "path"   -> "companies",
          "reason" -> "MISSING_REQUIRED_FIELD"
        )
      )
    }

  }
}
