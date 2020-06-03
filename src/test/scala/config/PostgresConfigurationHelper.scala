package config

import com.typesafe.config.ConfigFactory

/**
  * Created by kartik on Apr 24, 2020
  */

object PostgresConfigurationHelper {

  val config               = ConfigFactory.load("postgres-application.conf")
  val configWithDBProvider = ConfigFactory.load("postgres-dbprovider.conf")
}
