package org.sunbird.collectioncsv.util


import org.sunbird.common.exception.{ClientException, ResponseCode, ServerException}
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.sunbird.collectioncsv.util.CollectionTOCConstants.BEARER
import org.sunbird.common.Platform
import org.sunbird.common.dto.Response
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.collection.JavaConverters._
import java.text.MessageFormat
import scala.collection.immutable.{HashMap, Map}
import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.ExecutionContext


object CollectionTOCUtil {

   def getFrameworkTopics(frameworkId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Response = {
    try {
      val headers = new util.HashMap[String, String]() {
        put(CollectionTOCConstants.CONTENT_TYPE_HEADER, CollectionTOCConstants.APPLICATION_JSON)
        put(AUTHORIZATION, CollectionTOCConstants.BEARER + Platform.config.getString(CollectionTOCConstants.SUNBIRD_AUTHORIZATION))
      }

      val requestUrl = Platform.config.getString(CollectionTOCConstants.LEARNING_SERVICE_BASE_URL) + Platform.config.getString(CollectionTOCConstants.FRAMEWORK_READ_API_URL) + "/" + frameworkId

      TelemetryManager.log("CollectionTOCUtil --> getRelatedFrameworkById --> requestUrl: " + requestUrl)
      TelemetryManager.log("CollectionTOCUtil --> getRelatedFrameworkById --> headers: " + headers)
      val httpResponse = oec.httpUtil.get(requestUrl,"categories=topic",headers)
      
      TelemetryManager.log("CollectionTOCUtil --> getRelatedFrameworkById --> httpResponse.getResponseCode: " + httpResponse.getResponseCode)
      if ( null== httpResponse || httpResponse.getResponseCode.code() != ResponseCode.OK.code())
        throw new ServerException("SERVER_ERROR", "Error while fetching content data.")

      httpResponse
    } catch {
      case e: Exception =>
        TelemetryManager.log("CollectionTOCUtil --> handleReadRequest --> Exception: " + e.getMessage)
        throw e
    }
  }

  def validateDialCodes(channelId: String, dialcodes: List[String])(implicit oec: OntologyEngineContext, ec: ExecutionContext): List[String] = {
    val reqMap = new util.HashMap[String, AnyRef]() {
        put(CollectionTOCConstants.REQUEST, new util.HashMap[String, AnyRef]() {
            put(CollectionTOCConstants.SEARCH, new util.HashMap[String, AnyRef]() {
                put(CollectionTOCConstants.IDENTIFIER, dialcodes.distinct.asJava)
            })
        })
    }

    val headerParam = HashMap[String, String](CollectionTOCConstants.X_CHANNEL_ID -> channelId, AUTHORIZATION -> (CollectionTOCConstants.BEARER + Platform.config.getString(CollectionTOCConstants.SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = Platform.config.getString(CollectionTOCConstants.SUNBIRD_CS_BASE_URL) + Platform.config.getString(CollectionTOCConstants.SUNBIRD_DIALCODE_SEARCH_API)
    val searchResponse = oec.httpUtil.post(requestUrl, reqMap, headerParam)

    if (null == searchResponse || searchResponse.getResponseCode.code() != ResponseCode.OK.code())
      throw new ServerException("SERVER_ERROR", "Error while fetching DIAL Codes List.")

    try {
      val returnDIALCodes = searchResponse.getResult.getOrDefault(CollectionTOCConstants.DIAL_CODES, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]
      returnDIALCodes.asScala.toList.map(rec => rec.asScala.toMap[String,AnyRef]).map(_.getOrElse(CollectionTOCConstants.IDENTIFIER, "")).asInstanceOf[List[String]]
    }
    catch {
      case e:Exception => println("CollectionTOCUtil: validateDIALCodes --> exception: " + e.getMessage)
        List.empty
    }
  }

  def searchLinkedContents(linkedContents: List[String])(implicit oec: OntologyEngineContext, ec: ExecutionContext): List[Map[String, AnyRef]] = {
    val reqMap = new util.HashMap[String, AnyRef]() {
        put(CollectionTOCConstants.REQUEST, new util.HashMap[String, AnyRef]() {
            put(CollectionTOCConstants.FILTERS, new util.HashMap[String, AnyRef]() {
                put(CollectionTOCConstants.IDENTIFIER, linkedContents.distinct.asJava)
            })
            put(CollectionTOCConstants.FIELDS, new util.ArrayList[String]() {
              add(CollectionTOCConstants.IDENTIFIER)
              add(CollectionTOCConstants.NAME)
              add(CollectionTOCConstants.CONTENT_TYPE)
              add(CollectionTOCConstants.MIME_TYPE)
            })
            put(CollectionTOCConstants.LIMIT, linkedContents.size.asInstanceOf[AnyRef])
        })
    }

    val headerParam = HashMap[String, String](AUTHORIZATION -> (BEARER + Platform.config.getString(CollectionTOCConstants.SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = Platform.config.getString(CollectionTOCConstants.SUNBIRD_CS_BASE_URL) + Platform.config.getString(CollectionTOCConstants.SUNBIRD_CONTENT_SEARCH_URL)

    val searchResponse =  oec.httpUtil.post(requestUrl, reqMap, headerParam)

    if (null == searchResponse || searchResponse.getResponseCode.code() != ResponseCode.OK.code())
      throw new ServerException("SERVER_ERROR", "Error while fetching Linked Contents List.")

    try {
      searchResponse.getResult.getOrDefault(CollectionTOCConstants.CONTENT, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]].asScala.toList.map(rec => rec.asScala.toMap[String,AnyRef])
    }
    catch {
      case _:Exception =>
        List.empty
    }
  }

  def linkDIALCode(channelId: String, collectionID: String, linkDIALCodesMap: List[Map[String,String]])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Response = {
    val reqMap = new util.HashMap[String, AnyRef]() {
        put(CollectionTOCConstants.REQUEST, new util.HashMap[String, AnyRef]() {
            put(CollectionTOCConstants.CONTENT, linkDIALCodesMap.asJava)
        })
    }

    val headerParam = HashMap[String, String](CollectionTOCConstants.X_CHANNEL_ID -> channelId, AUTHORIZATION -> (BEARER + Platform.config.getString(CollectionTOCConstants.SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = Platform.config.getString(CollectionTOCConstants.LEARNING_SERVICE_BASE_URL) + Platform.config.getString(CollectionTOCConstants.LINK_DIAL_CODE_API) + "/" + collectionID

    val linkResponse = oec.httpUtil.post(requestUrl, reqMap, headerParam)

    if (null == linkResponse || linkResponse.getResponseCode.code() != ResponseCode.OK.code())
      if(linkResponse.getResponseCode.code() == 400) {
        val msgsResult = linkResponse.getResult.getOrDefault(CollectionTOCConstants.MESSAGES, new util.ArrayList[String])
        throw new ClientException("DIAL_CODE_LINK_ERROR", MessageFormat.format("{0}",msgsResult))
      } else throw new ServerException("SERVER_ERROR", "Error while updating collection hierarchy.")

    linkResponse
  }
}
