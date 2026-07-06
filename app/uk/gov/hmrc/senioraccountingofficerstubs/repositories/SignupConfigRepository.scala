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

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import play.api.libs.json.Format
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.senioraccountingofficerstubs.config.AppConfig
import uk.gov.hmrc.senioraccountingofficerstubs.models.testOnly.SignupStubConfiguration

import scala.concurrent.{ExecutionContext, Future}

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

@Singleton
class SignupConfigRepository @Inject() (
    mongoComponent: MongoComponent,
    appConfig: AppConfig,
    clock: Clock
)(using ec: ExecutionContext)
    extends PlayMongoRepository[SignupStubConfiguration](
      collectionName = "signup-config",
      mongoComponent = mongoComponent,
      domainFormat = SignupStubConfiguration.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("safeId")
        ),
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("lastUpdatedIdx")
            .expireAfter(appConfig.cacheTtl, TimeUnit.SECONDS)
        )
      )
    ) {

  given instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byId(safeId: String): Bson = Filters.equal("safeId", safeId)

  def keepAlive(safeId: String): Future[Boolean] = Mdc.preservingMdc {
    collection
      .updateOne(
        filter = byId(safeId),
        update = Updates.set("lastUpdated", Instant.now(clock))
      )
      .toFuture()
      .map(_ => true)
  }

  def get(subscriptionId: String): Future[Option[SignupStubConfiguration]] = Mdc.preservingMdc {
    keepAlive(subscriptionId).flatMap { _ =>
      collection
        .find(byId(subscriptionId))
        .headOption()
    }
  }

  def set(config: SignupStubConfiguration): Future[Boolean] = Mdc.preservingMdc {

    val updatedConfig = config copy (lastUpdated = Instant.now(clock))

    collection
      .replaceOne(
        filter = byId(updatedConfig.safeId),
        replacement = updatedConfig,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => true)
  }

  def clear(safeId: String): Future[Boolean] = Mdc.preservingMdc {
    collection
      .deleteOne(byId(safeId))
      .toFuture()
      .map(_ => true)
  }

}
