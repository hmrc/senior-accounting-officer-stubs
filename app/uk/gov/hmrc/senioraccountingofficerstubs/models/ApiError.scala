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

package uk.gov.hmrc.senioraccountingofficerstubs.models

import uk.gov.hmrc.senioraccountingofficerstubs.models.hip.*

final case class ApiError(path: Option[String], reason: String)

object ApiError {
  extension (apiError: ApiError) {
    def toHip: StandardHipFailures = {
      StandardHipFailures(
        origin = "HIP",
        response = Failures(failures = Seq(apiError.toHipFailure))
      )
    }

    def toHipFailure: Failure = {
      Failure(`type` = apiError.reason, reason = apiError.path.fold("")(identity))
    }
  }

  extension (apiErrors: Seq[ApiError]) {
    def toHip: StandardHipFailures = {
      StandardHipFailures(
        origin = "HIP",
        response = Failures(failures = apiErrors.map(_.toHipFailure))
      )
    }
  }
}
