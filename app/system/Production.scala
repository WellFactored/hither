package system

import models.Repository
import play.api.{Logger, Application}
import services.ContentEnumerator
import system.registry.{Registry, S3Registry}

import scala.concurrent.{ExecutionContext, Future}

object Production {

  lazy val s3Index = new S3Index {

    import fly.play.s3.S3

    override def bucketName: String = Configuration.s3.bucketName

    override implicit def app: Application = play.api.Play.current

    override lazy val s3: S3 = S3.fromConfig

    override lazy val logger = Logger
  }

  lazy val fileIndex = new LocalIndex {
    override def imagesStream(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = ???

    override def tag(repo: Repository, tagName: String)(implicit ctx: ExecutionContext): Future[Option[String]] = ???

    override def writeTag(repo: Repository, tagName: String, value: String)(implicit ctx: ExecutionContext): Future[Unit] = ???

    override def tagsStream(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = ???
  }

  lazy val index = Configuration.storage match {
    case "s3" => s3Index
    case "file" => fileIndex
  }

  lazy val s3Registry = new S3Registry {

    import fly.play.s3.S3

    override implicit def app = play.api.Play.current

    override val bucketName: String = Configuration.s3.bucketName

    override val s3 = S3.fromConfig

    override val logger = Logger

    Logger.debug("Initialising S3 registry")
    Logger.debug(s"Using aws.accessKeyId ${obfuscate(Configuration.aws.accessKeyId)}")
    Logger.debug(s"Using aws.secretKey ${obfuscate(Configuration.aws.secretKey)}")
    Logger.debug(s"Using region ${Configuration.s3.region}")
    Logger.debug(s"Using bucket $bucketName")
  }

  def obfuscate(s: String, show: Int = 3): String = {
    val hide = if (s.length > show) (s.length - show) else s.length
    List.fill(hide)('*').mkString + s.substring(hide)
  }

  lazy val registry: Registry = Configuration.storage match {
    case "s3" => s3Registry
    case "file" => throw new UnsupportedOperationException("File storage is not yet supported")
    case s => throw new IllegalArgumentException(s"Don't recognise storage type '$s'")
  }

}
