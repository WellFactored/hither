package controllers

import java.io.FileOutputStream
import scala.concurrent.Future

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

import play.api.Logger
import play.api.mvc.{Result, Action, Controller}
import play.api.libs.ws.WS
import play.api.libs.json.{Json, JsString}
import play.api.libs.iteratee.{Iteratee, Enumerator}

import system.LocalRegistry
import system.LocalRegistry.RegistryFile
import models.ImageId
import services.NotFoundException


object Images extends Controller {
  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    Logger.info(s"get ancestry for ${imageId.id}")
    buildAncestry(imageId).map { l => Ok(Json.toJson(l))}.recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def buildAncestry(imageId: ImageId): Future[List[String]] = {
    Logger.info(s"Building ancestry for ${imageId.id}")
    LocalRegistry.findLocalSource(imageId, "ancestry") match {
      case Some(localAncestry) => {
        Logger.info(s"Serving ancestry from ${localAncestry.getAbsolutePath()}")
        Future(Json.parse(localAncestry.source.mkString).as[List[String]])
      }
      case None => LocalRegistry.findLocalSource(imageId, "json") match {
        case Some(r: RegistryFile) => Json.parse(r.source.mkString) \ "parent" match {
          case JsString(parentId) => Logger.info(s"Parent is $parentId"); buildAncestry(ImageId(parentId)).map(imageId.id +: _)
          case _ => Future(List())
        }
        case _ => findData(imageId, "ancestry").flatMap { t =>
          val (e, _, _) = t

          e.run(Iteratee.getChunks).map { byteArrays =>
            val s = byteArrays.map(new String(_)).mkString
            Json.parse(s).as[List[String]]
          }
        }
      }
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    findData(imageId, "json").map(feedResult).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    findData(imageId, "layer", "binary/octet-stream").map(feedResult).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  private def findData(imageId: ImageId, extension: String, contentType: String = "application/json"): Future[(Enumerator[Array[Byte]], String, Option[String])] = {
    val result = LocalRegistry.findLocalSource(imageId, extension) match {
      case Some(localSource) =>
        Logger.info(s"Supplying $extension for ${imageId.id} from ${localSource.kind}")
        Future((Enumerator.fromFile(localSource.file), contentType, Some(localSource.length().toString)))

      case None =>
        Logger.info(s"Going to docker for ${imageId.id}/$extension")
        respondFromUrl(s"${imageId.id}.$extension", s"http://registry-1.docker.io/v1/images/${imageId.id}/$extension")
    }
    result
  }

  def feedResult(result: (Enumerator[Array[Byte]], String, Option[String])): Result = {
    result match {
      case (e, contentType, Some(length)) => Ok.feed(e).as(contentType).withHeaders("Content-Length" -> length)
      case (e, contentType, None) => Ok.chunked(e).as(contentType)
    }
  }

  def putJson(imageId: ImageId) = Action(parse.file(LocalRegistry.buildRegistryPath(s"${imageId.id}.json").file)) {
    request =>
      Logger.info(s"Layer json pushed to ${request.body.getAbsolutePath}")
      Ok(JsString(""))
  }


  def putLayer(imageId: ImageId) = Action(parse.file(LocalRegistry.buildRegistryPath(s"${imageId.id}.layer").file)) {
    request =>
      Logger.info(s"Layer pushed to ${request.body.getAbsolutePath}")
      Ok(JsString(""))
  }

  def putChecksum(imageId: ImageId) = Action(parse.file(LocalRegistry.buildRegistryPath(s"${imageId.id}.checksum").file)) {
    request =>
      Logger.info(s"Checksum pushed to ${request.body.getAbsolutePath}")
      Ok(JsString(""))
  }

  def respondFromUrl(cacheFileName: String, url: String): Future[(Enumerator[Array[Byte]], String, Option[String])] = {
    WS.url(url).getStream().map {
      case (response, body) =>

        // Check that the response was successful
        if (response.status == 200) {
          // Get the content type
          val contentType = response.headers.get("Content-Type").flatMap(_.headOption)
            .getOrElse("binary/octet-stream")

          val cacheFile = LocalRegistry.buildCachePath(cacheFileName)
          val os = new FileOutputStream(cacheFile.file)

          val fileWriter: Enumerator[Array[Byte]] = body.map {
            bytes =>
              os.write(bytes)
              bytes
          }

          fileWriter.onDoneEnumerating(os.close())

          // If there's a content length, send that, otherwise return the body chunked
          response.headers.get("Content-Length") match {
            case Some(Seq(length)) => (fileWriter, contentType, Some(length))

            case _ => (fileWriter, contentType, None)

          }
        } else {
          throw NotFoundException(url)
        }
    }
  }
}
