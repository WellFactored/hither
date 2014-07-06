package controllers

import models.ImageId
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsString
import play.api.mvc.{Action, BodyParser, Controller, Result}
import services._
import system.Configuration
import system.registry.{Registry, ResourceType, S3Registry}

import scala.util.Try

object ProductionRegistry extends S3Registry {

  import fly.play.s3.S3


  override implicit def app = play.api.Play.current

  override lazy val bucketName: String = Configuration.s3.bucketName
  override lazy val s3 = S3.fromConfig

  def init {}
}

object RegistryController extends RegistryController {
  override def registry = ProductionRegistry
}

trait RegistryController extends Controller {
  def registry: Registry

  import system.registry.ResourceType._

  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    Logger.info(s"get ancestry for ${imageId.id}")
    registry.ancestry(imageId).map {
      case Some(ce) => feedContent(ce)
      case None => NotFound(s"no ancestry for ${imageId.id}")
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    registry.json(imageId).map {
      case Some(js) => feedContent(js)
      case None => NotFound(s"no json for ${imageId.id}")
    }
  }

  def layerHead(imageId: ImageId) = Action.async { implicit request =>
    registry.layerHead(imageId).map {
      case Some(l) => Ok("").withHeaders("Content-Type" -> "binary/octet-stream", "Content-Length" -> l.toString)
      case None => NotFound
    }
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    registry.layer(imageId).map {
      case Some(layer) => feedContent(layer)
      case None => NotFound(s"no json for ${imageId.id}")
    }
  }

  def putJson(imageId: ImageId) = Action(toRegistry(imageId, JsonType, registry)) { request =>
    Logger.info(s"Layer json for ${imageId.id} pushed to registry")
    Ok(JsString(""))
  }

  def putLayer(imageId: ImageId) = Action(toRegistry(imageId, LayerType, registry)) { request =>
    Logger.info(s"Layer ${imageId.id} pushed to registry")
    Ok(JsString(""))
  }


  def putChecksum(imageId: ImageId) = Action(toRegistry(imageId, ChecksumType, registry)) { request =>
    Ok(JsString(""))
  }

  protected def toRegistry(imageId: ImageId, resourceType: ResourceType, registry: Registry): BodyParser[Unit] =
    BodyParser("to registry") { request =>
      val contentLength = request.headers.get("Content-Length").flatMap(s => Try(s.toLong).toOption)
      registry.sinkFor(imageId, resourceType, contentLength).map { _ => Right(Unit)}
    }


  protected def feedContent(content: ContentEnumerator): Result = {
    content match {
      case ContentEnumerator(e, contentType, Some(length)) => Ok.feed(e).as(contentType).withHeaders("Content-Length" -> length.toString)
      case ContentEnumerator(e, contentType, None) => Ok.chunked(e).as(contentType)
    }
  }
}
