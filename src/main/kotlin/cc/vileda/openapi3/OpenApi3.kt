package cc.vileda.openapi3

import cc.vileda.openapi3.OpenApi3.Companion.mapper
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

data class OpenApi3Info(
        var title: String = "",
        var version: String = ""
)

interface OpenApi3MediaType {
    val schema: String
}

interface OpenApi3ParameterSchema {
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

data class OpenApi3TypedMediaType<T>(
        @field:JsonIgnore
        val clazz: Class<T>
) : OpenApi3MediaType {
    override val schema: String
    @field:JsonIgnore
    val schemaJson: JSONObject

    init {
        val genSchema = schemaFrom(clazz)
        schemaJson = genSchema.schemaJson
        schema = genSchema.schema
    }
}

data class OpenApi3TypedParameterSchema<T>(
        @field:JsonIgnore
        val clazz: Class<T>
) : OpenApi3ParameterSchema {
    override val schema: String
    @field:JsonIgnore
    val schemaJson: JSONObject

    init {
        val genSchema = schemaFrom(clazz)
        schemaJson = genSchema.schemaJson
        schema = genSchema.schema
    }
}

class OpenApi3MediaTypeSerializer(mt: Class<OpenApi3MediaType>? = null) : StdSerializer<OpenApi3MediaType>(mt) {
    override fun serialize(value: OpenApi3MediaType, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject(mapOf("schema" to mapOf("\$ref" to value.schema))).toString())
    }
}

class OpenApi3ParameterSchemaSerializer(mt: Class<OpenApi3ParameterSchema>? = null) : StdSerializer<OpenApi3ParameterSchema>(mt) {
    override fun serialize(value: OpenApi3ParameterSchema, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject(mapOf("\$ref" to value.schema)).toString())
    }
}

class OpenApi3ComponentsSerializer(mt: Class<OpenApi3Components>? = null) : StdSerializer<OpenApi3Components>(mt) {
    override fun serialize(value: OpenApi3Components, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject().put("schemas", value.schemas).toString())
    }
}

data class OpenApi3Parameter(
        var name: String = "",
        var `in`: String = "path",
        var description: String = "",
        var required: Boolean = true,
        var style: String = "simple",
        var schema: OpenApi3TypedParameterSchema<*> = OpenApi3TypedParameterSchema(String::class.java)
) {
    inline fun <reified T> schema() {
        schema = OpenApi3TypedParameterSchema(T::class.java)
    }
}

data class OpenApi3Response(
        var description: String = ""
) {
    val content = HashMap<String, OpenApi3TypedMediaType<*>>()
    inline fun <reified T> response(mediaType: String) {
        val apiMediaType = OpenApi3TypedMediaType(T::class.java)
        content.put(mediaType, apiMediaType)
    }
}

data class OpenApi3RequestBodies(
        val content: MutableMap<String, OpenApi3TypedMediaType<*>> = HashMap()
) {
    var description: String? = null
    inline fun <reified T> request(mediaType: String) {
        val apiMediaType = OpenApi3TypedMediaType(T::class.java)
        content.put(mediaType, apiMediaType)
    }
}

data class OpenApi3Responses(
        private val responses: MutableMap<String, OpenApi3Response> = HashMap()
) : MutableMap<String, OpenApi3Response> by responses

data class OpenApi3Path(
        var description: String = "",
        var operationId: String = "",
        var tags: List<String> = emptyList(),
        var summary: String = ""
) {
    val responses = OpenApi3Responses()
    var requestBody: OpenApi3RequestBodies? = null
    var parameters: MutableList<OpenApi3Parameter>? = null
    fun code(code: String, init: OpenApi3Response.() -> Unit) {
        val response = OpenApi3Response()
        response.init()
        responses.put(code, response)
    }

    fun created(init: OpenApi3Response.() -> Unit) = code("201", init)
    fun ok(init: OpenApi3Response.() -> Unit) = code("200", init)

    fun requestBody(init: OpenApi3RequestBodies.() -> Unit) {
        if (requestBody == null) {
            requestBody = OpenApi3RequestBodies()
            requestBody!!.init()
        }
    }

    fun parameter(init: OpenApi3Parameter.() -> Unit) {
        if (parameters == null) {
            val parameter = OpenApi3Parameter()
            parameter.init()
            parameters = mutableListOf(parameter)
        }
    }
}

open class OpenApi3MethodPath(
        @field:JsonIgnore
        val path: OpenApi3Path,
        @field:JsonIgnore
        val jsonKey: String
)

data class OpenApi3Paths(
        private val paths: MutableMap<String, MutableMap<String, OpenApi3Path>> = HashMap()
) : MutableMap<String, MutableMap<String, OpenApi3Path>> by paths {
    private fun initOpenApi3Path(init: OpenApi3Path.() -> Unit): OpenApi3Path {
        val apiPath = OpenApi3Path()
        apiPath.init()
        return apiPath
    }

    private fun putPath(path: String, methodPath: OpenApi3MethodPath) {
        if (containsKey(path)) {
            get(path)?.put(methodPath.jsonKey, methodPath.path)
        } else {
            put(path, mutableMapOf(methodPath.jsonKey to methodPath.path))
        }
    }

    fun get(path: String, init: OpenApi3Path.() -> Unit) {
        putPath(path, OpenApi3MethodPath(initOpenApi3Path(init), "get"))
    }

    fun put(path: String, init: OpenApi3Path.() -> Unit) {
        putPath(path, OpenApi3MethodPath(initOpenApi3Path(init), "put"))
    }

    fun post(path: String, init: OpenApi3Path.() -> Unit) {
        putPath(path, OpenApi3MethodPath(initOpenApi3Path(init), "post"))
    }

    fun delete(path: String, init: OpenApi3Path.() -> Unit) {
        putPath(path, OpenApi3MethodPath(initOpenApi3Path(init), "delete"))
    }

    fun patch(path: String, init: OpenApi3Path.() -> Unit) {
        putPath(path, OpenApi3MethodPath(initOpenApi3Path(init), "patch"))
    }

    fun head(path: String, init: OpenApi3Path.() -> Unit) {
        putPath(path, OpenApi3MethodPath(initOpenApi3Path(init), "head"))
    }

    fun options(path: String, init: OpenApi3Path.() -> Unit) {
        putPath(path, OpenApi3MethodPath(initOpenApi3Path(init), "options"))
    }

    fun trace(path: String, init: OpenApi3Path.() -> Unit) {
        putPath(path, OpenApi3MethodPath(initOpenApi3Path(init), "trace"))
    }
}

data class OpenApi3Components(
        val schemas: Map<String, Any> = HashMap()
)

data class OpenApi3(
        var openapi: String = "3.0.0",
        var info: OpenApi3Info = OpenApi3Info(),
        var paths: OpenApi3Paths = OpenApi3Paths()
) {
    companion object {
        @field:JsonIgnore
        val mapper = ObjectMapper()
    }

    init {
        val module = SimpleModule()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        module.addSerializer(OpenApi3MediaType::class.java, OpenApi3MediaTypeSerializer())
        module.addSerializer(OpenApi3ParameterSchema::class.java, OpenApi3ParameterSchemaSerializer())
        module.addSerializer(OpenApi3Components::class.java, OpenApi3ComponentsSerializer())
        mapper.registerModule(module)
    }

    val components: OpenApi3Components
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

            return OpenApi3Components(responseSchemas
                    .plus(requestSchemas)
                    .plus(parameterSchemas))
        }

    fun info(init: OpenApi3Info.() -> Unit) {
        info.init()
    }

    fun paths(init: OpenApi3Paths.() -> Unit) {
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

fun openapi3(init: OpenApi3.() -> Unit): OpenApi3 {
    val openapi3 = OpenApi3()
    openapi3.init()
    return openapi3
}
