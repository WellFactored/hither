package system

import play.api.Play
import play.api.Play.current

object Configuration {

  lazy val storage = Play.configuration.getString("hither.storage").getOrElse("s3")

  object file {
    lazy val indexRoot = Play.configuration.getString("file.registry.index").getOrElse("/tmp/index")

    lazy val registryRoot = Play.configuration.getString("file.registry.root").getOrElse("/tmp/registry")
  }


  object s3 {

    lazy val indexRoot = Play.configuration.getString("s3.registry.index").getOrElse("index")

    lazy val registryRoot = Play.configuration.getString("s3.registry.root").getOrElse("registry")

    lazy val bucketName = Play.configuration.getString("s3.bucketName").get
    lazy val region = Play.configuration.getString("s3.region").get
  }

}
