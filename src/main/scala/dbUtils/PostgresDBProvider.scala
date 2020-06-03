package dbUtils

import akka.actor.ActorSystem
import akka.persistence.jdbc.util.{EagerSlickDatabase, SlickDatabase, SlickDatabaseProvider}
import com.typesafe.config.Config
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile

/**
  * Created by kartik on Apr 24, 2020
  */

class PostgresDBProvider(system: ActorSystem) extends SlickDatabaseProvider {
  val jdbcProfile              = PostgresProfile
  val jdbcBackend: DatabaseDef = DataSourceHelper.getDBConnection

  override def database(config: Config): SlickDatabase = {
    EagerSlickDatabase(jdbcBackend, PostgresProfile)
  }
}

object DBProperties {
  val dbUrl             = "jdbc:postgresql://localhost:5432/charvaka?reWriteBatchedInserts=true"
  val user              = "postgres"
  val password          = "admin"
  val idleTimeout       = 600000
  val maxPoolSize       = 20
  val connectionTimeout = 1000
}

object DataSourceHelper {

  import DBProperties._

  private def buildDataSource: HikariDataSource = {
    val hkConfig = new HikariConfig()
    hkConfig.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource")
    hkConfig.addDataSourceProperty("url", dbUrl)
    hkConfig.addDataSourceProperty("user", user)
    hkConfig.addDataSourceProperty("password", password)
    hkConfig.setIdleTimeout(idleTimeout) //10 minutes
    // hkConfig.setMinimumIdle(2000)
    hkConfig.setMaximumPoolSize(maxPoolSize)
    hkConfig.setMaxLifetime(1800000)
    hkConfig.setConnectionTimeout(connectionTimeout)
    val ds = new HikariDataSource(hkConfig)
    ds
  }

  def getDBConnection: DatabaseDef = {
    val dataSource: HikariDataSource = buildDataSource
    val globalDB                     = PostgresProfile.api.Database.forDataSource(dataSource, Some(maxPoolSize))
    globalDB.asInstanceOf[DatabaseDef]
  }
}