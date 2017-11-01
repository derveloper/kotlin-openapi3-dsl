package cc.vileda.openapi3

import cc.vileda.openapi3.OpenApiObject.Companion.mapper
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator
import com.reprezen.kaizen.oasparser.OpenApi3Parser
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

data class InfoObject(
        var title: String = "",
        var version: String = ""
)

interface SchemaObject {
    val schema: String
}

interface ParameterSchemaObject {
    val schema: String
}

private data class Schema(val schemaJson: JSONObject, val schema: String)

private fun schemaFrom(clazz: Class<*>): Schema {
    val schemaGen = JsonSchemaGenerator(mapper)
    val s = schemaGen.generateSchema(clazz)
    val jsonSchema = JSONObject(mapper.writeValueAsString(s))
    jsonSchema.remove("id")
    return Schema(JSONObject(mapOf("schema" to jsonSchema)), "#/components/schemas/${clazz.simpleName}")
}

data class TypedSchemaObject<T>(
        @field:JsonIgnore
        val clazz: Class<T>
) : SchemaObject {
    override val schema: String
    @field:JsonIgnore
    val schemaJson: JSONObject

    init {
        val genSchema = schemaFrom(clazz)
        schemaJson = genSchema.schemaJson
        schema = genSchema.schema
    }
}

data class TypedParameterSchemaObject<T>(
        @field:JsonIgnore
        val clazz: Class<T>
) : ParameterSchemaObject {
    override val schema: String
    @field:JsonIgnore
    val schemaJson: JSONObject

    init {
        val genSchema = schemaFrom(clazz)
        schemaJson = genSchema.schemaJson
        schema = genSchema.schema
    }
}

class SchemaObjectSerializer(mt: Class<SchemaObject>? = null) : StdSerializer<SchemaObject>(mt) {
    override fun serialize(value: SchemaObject, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject(mapOf("schema" to mapOf("\$ref" to value.schema))).toString())
    }
}

class ParameterSchemaObjectSerializer(mt: Class<ParameterSchemaObject>? = null) : StdSerializer<ParameterSchemaObject>(mt) {
    override fun serialize(value: ParameterSchemaObject, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject(mapOf("\$ref" to value.schema)).toString())
    }
}

class ComponentsObjectSerializer(mt: Class<ComponentsObject>? = null) : StdSerializer<ComponentsObject>(mt) {
    override fun serialize(value: ComponentsObject, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject().put("schemas", value.schemas).toString())
    }
}

data class SecurityRequirementObject(
        private var nameToRequirements: MutableMap<String, List<String>> = mutableMapOf()
) : MutableMap<String, List<String>> by nameToRequirements

data class ParameterObject(
        var name: String = "",
        var `in`: String = "path",
        var description: String = "",
        var required: Boolean = true,
        var style: String = "simple",
        var schema: TypedParameterSchemaObject<*> = TypedParameterSchemaObject(String::class.java)
) {
    inline fun <reified T> schema() {
        schema = TypedParameterSchemaObject(T::class.java)
    }
}

data class ResponseObject(
        var description: String = ""
) {
    val content = HashMap<String, TypedSchemaObject<*>>()
    inline fun <reified T> response(mediaType: String) {
        val apiMediaType = TypedSchemaObject(T::class.java)
        content.put(mediaType, apiMediaType)
    }
}

data class RequestBodyObject(
        val content: MutableMap<String, TypedSchemaObject<*>> = HashMap()
) {
    var description: String? = null
    inline fun <reified T> request(mediaType: String) {
        val apiMediaType = TypedSchemaObject(T::class.java)
        content.put(mediaType, apiMediaType)
    }
}

data class ResponsesObject(
        private val responses: MutableMap<String, ResponseObject> = HashMap()
) : MutableMap<String, ResponseObject> by responses

data class OperationObject(
        var description: String = "",
        var operationId: String = "",
        var tags: List<String> = emptyList(),
        var summary: String = "",
        var deprecated: Boolean = false,
        var servers: List<ServerObject> = emptyList(),
        var externalDocs: ExternalDocumentationObject? = null,
        var security: List<SecurityRequirementObject> = emptyList()
) {
    val responses = ResponsesObject()
    var requestBody: RequestBodyObject? = null
    var parameters: MutableList<ParameterObject>? = null
    fun code(code: String, init: ResponseObject.() -> Unit) {
        val response = ResponseObject()
        response.init()
        responses.put(code, response)
    }

    fun created(init: ResponseObject.() -> Unit) = code("201", init)
    fun ok(init: ResponseObject.() -> Unit) = code("200", init)

    fun requestBody(init: RequestBodyObject.() -> Unit) {
        if (requestBody == null) {
            requestBody = RequestBodyObject()
            requestBody!!.init()
        }
    }

    fun parameter(init: ParameterObject.() -> Unit) {
        if (parameters == null) {
            val parameter = ParameterObject()
            parameter.init()
            parameters = mutableListOf(parameter)
        }
    }
}

open class PathItemObject(
        @field:JsonIgnore
        val path: OperationObject,
        @field:JsonIgnore
        val jsonKey: String
)

data class PathsObject(
        private val paths: MutableMap<String, MutableMap<String, OperationObject>> = HashMap()
) : MutableMap<String, MutableMap<String, OperationObject>> by paths {
    private fun makeOperationObject(init: OperationObject.() -> Unit): OperationObject {
        val apiPath = OperationObject()
        apiPath.init()
        return apiPath
    }

    private fun putPath(path: String, pathItemObject: PathItemObject) {
        if (containsKey(path)) {
            get(path)?.put(pathItemObject.jsonKey, pathItemObject.path)
        } else {
            put(path, mutableMapOf(pathItemObject.jsonKey to pathItemObject.path))
        }
    }

    fun get(path: String, init: OperationObject.() -> Unit) {
        putPath(path, PathItemObject(makeOperationObject(init), "get"))
    }

    fun put(path: String, init: OperationObject.() -> Unit) {
        putPath(path, PathItemObject(makeOperationObject(init), "put"))
    }

    fun post(path: String, init: OperationObject.() -> Unit) {
        putPath(path, PathItemObject(makeOperationObject(init), "post"))
    }

    fun delete(path: String, init: OperationObject.() -> Unit) {
        putPath(path, PathItemObject(makeOperationObject(init), "delete"))
    }

    fun patch(path: String, init: OperationObject.() -> Unit) {
        putPath(path, PathItemObject(makeOperationObject(init), "patch"))
    }

    fun head(path: String, init: OperationObject.() -> Unit) {
        putPath(path, PathItemObject(makeOperationObject(init), "head"))
    }

    fun options(path: String, init: OperationObject.() -> Unit) {
        putPath(path, PathItemObject(makeOperationObject(init), "options"))
    }

    fun trace(path: String, init: OperationObject.() -> Unit) {
        putPath(path, PathItemObject(makeOperationObject(init), "trace"))
    }
}

data class ComponentsObject(
        val schemas: Map<String, Any> = HashMap()
)

data class ExternalDocumentationObject(
        var url: String,
        var description: String = ""
)

data class TagObject(
        var name: String = "",
        var description: String = "",
        var externalDocs: ExternalDocumentationObject? = null
)

data class ServerVariableObject(
        var default: String,
        var enum: List<String> = emptyList(),
        var description: String = ""
)

data class ServerObject(
        var url: String = "",
        var description: String = "",
        val variables: Map<String, ServerVariableObject> = emptyMap()
)

data class OpenApiObject(
        var openapi: String = "3.0.0",
        var info: InfoObject = InfoObject(),
        var paths: PathsObject = PathsObject(),
        var tags: List<TagObject> = emptyList(),
        var externalDocs: ExternalDocumentationObject? = null,
        var servers: List<ServerObject> = mutableListOf()
) {
    companion object {
        @field:JsonIgnore
        val mapper = ObjectMapper()
    }

    init {
        val module = SimpleModule()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        module.addSerializer(SchemaObject::class.java, SchemaObjectSerializer())
        module.addSerializer(ParameterSchemaObject::class.java, ParameterSchemaObjectSerializer())
        module.addSerializer(ComponentsObject::class.java, ComponentsObjectSerializer())
        mapper.registerModule(module)
    }

    val components: ComponentsObject
        get() {
            val responseSchemas: Map<String, Any> = paths.values
                    .flatMap { it.values }
                    .flatMap { it.responses.values }
                    .flatMap { it.content.values }
                    .fold(mutableMapOf()) { m, o ->
                        m.put(o.clazz.simpleName, o.schemaJson.getJSONObject("schema"))
                        m
                    }

            val requestSchemas: Map<String, Any> = paths.values
                    .flatMap { it.values }
                    .mapNotNull { it.requestBody }
                    .flatMap { it.content.values }
                    .fold(mutableMapOf()) { m, o ->
                        m.put(o.clazz.simpleName, o.schemaJson.getJSONObject("schema"))
                        m
                    }

            val parameterSchemas: Map<String, Any> = paths.values
                    .flatMap { it.values }
                    .mapNotNull { it.parameters }
                    .flatMap { it }
                    .map { it.schema }
                    .fold(mutableMapOf()) { m, o ->
                        m.put(o.clazz.simpleName, o.schemaJson.getJSONObject("schema"))
                        m
                    }

            return ComponentsObject(responseSchemas
                    .plus(requestSchemas)
                    .plus(parameterSchemas))
        }

    fun info(init: InfoObject.() -> Unit) {
        info.init()
    }

    fun paths(init: PathsObject.() -> Unit) {
        paths.init()
    }

    private fun toJson(): JSONObject {
        val writeValueAsString = mapper.writeValueAsString(this)
        return JSONObject(writeValueAsString)
    }

    fun asJson(): JSONObject {
        val parse = OpenApi3Parser().parse(asFile())
        return JSONObject(parse.toJson().toString())
    }

    fun asFile(): File {
        val file = Files.createTempFile("openapi-", ".json").toFile()
        file.writeText(toJson().toString())
        file.deleteOnExit()
        return file
    }
}

fun openapi3(init: OpenApiObject.() -> Unit): OpenApiObject {
    val openapi3 = OpenApiObject()
    openapi3.init()
    return openapi3
}
