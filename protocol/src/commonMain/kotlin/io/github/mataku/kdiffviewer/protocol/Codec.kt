package io.github.mataku.kdiffviewer.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

object Codec {
  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  }

  fun encodeRequest(method: String, params: JsonObject = JsonObject(emptyMap())): String = json.encodeToString(RequestEnvelope.serializer(), RequestEnvelope(PROTOCOL_VERSION, method, params))

  fun decodeRequest(line: String): RequestEnvelope = json.decodeFromString(RequestEnvelope.serializer(), line)

  fun success(result: JsonElement): String = json.encodeToString(ResponseEnvelope.serializer(), ResponseEnvelope(ok = true, result = result))

  fun failure(code: String, message: String): String = json.encodeToString(ResponseEnvelope.serializer(), ResponseEnvelope(ok = false, error = ErrorBody(code, message)))

  fun decodeResponse(line: String): ResponseEnvelope = json.decodeFromString(ResponseEnvelope.serializer(), line)

  inline fun <reified T> params(request: RequestEnvelope): T = json.decodeFromJsonElement(request.params)

  inline fun <reified T> result(response: ResponseEnvelope): T = json.decodeFromJsonElement(requireNotNull(response.result) { "Response has no result" })
}
