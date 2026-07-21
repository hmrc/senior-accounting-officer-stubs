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

package uk.gov.hmrc.senioraccountingofficerstubs.repositories

import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters
import org.scalactic.source.Position
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.slf4j.MDC
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.dispatchers.MDCPropagatingExecutorService
import uk.gov.hmrc.senioraccountingofficerstubs.config.AppConfig
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.SignupStubConfiguration

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

class SignupConfigRepositoryISpec
    extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[SignupStubConfiguration]
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with MockitoSugar {

  private val instant          = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val testStubConfig = SignupStubConfiguration("utr", None, None, Instant.ofEpochSecond(1))

  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.cacheTtl) thenReturn 1L

  protected override val repository: SignupConfigRepository = new SignupConfigRepository(
    mongoComponent = mongoComponent,
    appConfig = mockAppConfig,
    clock = stubClock
  )(using scala.concurrent.ExecutionContext.Implicits.global)

  ".set" - {

    "must set the last updated time on the supplied user answers to `now`, and save them" in {

      val expectedResult = testStubConfig copy (lastUpdated = instant)

      val setResult     = repository.set(testStubConfig).futureValue
      val updatedRecord = find(Filters.equal("utr", testStubConfig.utr)).futureValue.headOption.value

      updatedRecord mustEqual expectedResult
    }

    mustPreserveMdc(repository.set(testStubConfig))
  }

  ".get" - {

    "when there is a record for this utr" - {

      "must update the lastUpdated time and get the record" in {

        insert(testStubConfig).futureValue

        val result         = repository.get(testStubConfig.utr).futureValue
        val expectedResult = testStubConfig copy (lastUpdated = instant)

        result.value mustEqual expectedResult
      }
    }

    "when there is no record for this utr" - {

      "must return None" in {

        repository.get("utr that does not exist").futureValue must not be defined
      }
    }

    mustPreserveMdc(repository.get(testStubConfig.utr))
  }

  ".clear" - {

    "must remove a record" in {

      insert(testStubConfig).futureValue

      val result = repository.clear(testStubConfig.utr).futureValue

      repository.get(testStubConfig.utr).futureValue must not be defined
    }

    "must return true when there is no record to remove" in {
      val result = repository.clear("utr that does not exist").futureValue

      result mustEqual true
    }

    mustPreserveMdc(repository.clear(testStubConfig.utr))
  }

  ".keepAlive" - {

    "when there is a record for this utr" - {

      "must update its lastUpdated to `now` and return true" in {

        insert(testStubConfig).futureValue

        val result = repository.keepAlive(testStubConfig.utr).futureValue

        val expectedUpdatedAnswers = testStubConfig copy (lastUpdated = instant)

        val updatedAnswers = find(Filters.equal("utr", testStubConfig.utr)).futureValue.headOption.value
        updatedAnswers mustEqual expectedUpdatedAnswers
      }
    }

    "when there is no record for this utr" - {

      "must return true" in {

        repository.keepAlive("utr that does not exist").futureValue mustEqual true
      }
    }

    mustPreserveMdc(repository.keepAlive(testStubConfig.utr))
  }

  private def mustPreserveMdc[A](f: => Future[A])(using pos: Position): Unit =
    "must preserve MDC" in {

      given ec: ExecutionContext =
        ExecutionContext.fromExecutor(new MDCPropagatingExecutorService(Executors.newFixedThreadPool(2)))

      MDC.put("test", "foo")
      while Option(MDC.get("test")).isEmpty do { Thread.sleep(10) }

      f.map { _ =>
        MDC.get("test") mustEqual "foo"
      }.futureValue
    }
}
