package cc.vileda.openapi3

import cc.vileda.openapi3.OpenApi.Companion.mapper
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
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

data class Info(
        var title: String = "",
        var version: String = ""
)

data class OAuthFlow(
        var authorizationUrl: String,
        var tokenUrl: String,
        var scopes: Map<String, String>,
        var refreshUrl: String? = null
)

data class OAuthFlows(
        var implicit: OAuthFlow? = null,
        var password: OAuthFlow? = null,
        var clientCredentials: OAuthFlow? = null,
        var authorizationCode: OAuthFlow? = null
)

data class SecurityScheme(
        var type: Type = Type.API_KEY,
        var name: String = "",
        var `in`: In = In.HEADER,
        var scheme: String = "Bearer",
        var description: String = "",
        var bearerFormat: String = "",
        var openIdConnectUrl: String = "",
        var flows: OAuthFlows? = null
) {
    fun flows(init: OAuthFlows.() -> Unit) {
        val oAuthFlows = OAuthFlows()
        oAuthFlows.init()
        flows = oAuthFlows
    }

    enum class Type(val type: String = "apiKey") {
        HTTP("http"),
        API_KEY("apiKey"),
        OAUTH2("oauth2"),
        OPEN_ID_CONNECT("openIdConnect");

        @JsonValue
        override fun toString(): String {
            return type
        }

    }

    enum class In(val type: String = "header") {
        QUERY("query"),
        HEADER("header"),
        COOKIE("cookie");

        @JsonValue
        override fun toString(): String {
            return type
        }
    }
}

interface Schema {
    val schema: String
}

interface ParameterSchema {
    val schema: String
}

private data class SchemaGen(val schemaJson: JSONObject, val schema: String)

private fun schemaFrom(clazz: Class<*>): SchemaGen {
    val schemaGen = JsonSchemaGenerator(mapper)
    val s = schemaGen.generateSchema(clazz)
    val jsonSchema = JSONObject(mapper.writeValueAsString(s))
    jsonSchema.remove("id")
    return SchemaGen(JSONObject(mapOf("schema" to jsonSchema)), "#/components/schemas/${clazz.simpleName}")
}

data class TypedSchema<T>(
        @field:JsonIgnore
        val clazz: Class<T>
) : Schema {
    override val schema: String
    @field:JsonIgnore
    val schemaJson: JSONObject

    init {
        val genSchema = schemaFrom(clazz)
        schemaJson = genSchema.schemaJson
        schema = genSchema.schema
    }
}

data class TypedParameterSchema<T>(
        @field:JsonIgnore
        val clazz: Class<T>
) : ParameterSchema {
    override val schema: String
    @field:JsonIgnore
    val schemaJson: JSONObject

    init {
        val genSchema = schemaFrom(clazz)
        schemaJson = genSchema.schemaJson
        schema = genSchema.schema
    }
}

class SchemaSerializer(mt: Class<Schema>? = null) : StdSerializer<Schema>(mt) {
    override fun serialize(value: Schema, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject(mapOf("schema" to mapOf("\$ref" to value.schema))).toString())
    }
}

class ParameterSchemaSerializer(mt: Class<ParameterSchema>? = null) : StdSerializer<ParameterSchema>(mt) {
    override fun serialize(value: ParameterSchema, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject(mapOf("\$ref" to value.schema)).toString())
    }
}

class ComponentsSerializer(mt: Class<Components>? = null) : StdSerializer<Components>(mt) {
    override fun serialize(value: Components, gen: JsonGenerator, provider: SerializerProvider?) {
        val obj = JSONObject().apply {
            put("schemas", JSONObject(value.schemas))
            put("securitySchemes", JSONObject(mapper.writeValueAsString(value.securitySchemes)))
        }
        gen.writeRawValue(obj.toString())
    }
}

data class SecurityRequirement(
        private var nameToRequirements: MutableMap<String, List<String>> = mutableMapOf()
) : MutableMap<String, List<String>> by nameToRequirements

data class Parameter(
        var name: String = "",
        var `in`: In = In.PATH,
        var description: String = "",
        var required: Boolean = true,
        var style: String = "simple",
        var schema: TypedParameterSchema<*> = TypedParameterSchema(String::class.java)
) {
    enum class In(val type: String = "path") {
        QUERY("query"),
        HEADER("header"),
        COOKIE("cookie"),
        PATH("path");

        @JsonValue
        override fun toString(): String {
            return type
        }
    }
    inline fun <reified T> schema() {
        schema = TypedParameterSchema(T::class.java)
    }
}

data class Response(
        var description: String = ""
) {
    val content = HashMap<String, TypedSchema<*>>()
    inline fun <reified T> response(mediaType: String) {
        val apiMediaType = TypedSchema(T::class.java)
        content.put(mediaType, apiMediaType)
    }
}

data class RequestBody(
        val content: MutableMap<String, TypedSchema<*>> = HashMap()
) {
    var description: String? = null
    inline fun <reified T> request(mediaType: String) {
        val apiMediaType = TypedSchema(T::class.java)
        content.put(mediaType, apiMediaType)
    }
}

data class Responses(
        private val responses: MutableMap<String, Response> = HashMap()
) : MutableMap<String, Response> by responses

data class Operation(
        var description: String = "",
        var operationId: String = "",
        var tags: List<String> = emptyList(),
        var summary: String = "",
        var deprecated: Boolean = false,
        val servers: MutableList<Server> = mutableListOf(),
        var externalDocs: ExternalDocumentation? = null,
        val security: MutableList<SecurityRequirement> = mutableListOf()
) {
    val responses = Responses()
    var requestBody: RequestBody? = null
    var parameters: MutableList<Parameter>? = null
    fun code(code: String, init: Response.() -> Unit) {
        val response = Response()
        response.init()
        responses.put(code, response)
    }

    fun created(init: Response.() -> Unit) = code("201", init)
    fun ok(init: Response.() -> Unit) = code("200", init)
    fun unauthorized(init: Response.() -> Unit) = code("401", init)
    fun forbidden(init: Response.() -> Unit) = code("403", init)

    fun requestBody(init: RequestBody.() -> Unit) {
        if (requestBody == null) {
            requestBody = RequestBody()
            requestBody!!.init()
        }
    }

    fun parameter(init: Parameter.() -> Unit) {
        if (parameters == null) {
            val parameter = Parameter()
            parameter.init()
            parameters = mutableListOf(parameter)
        }
    }

    fun server(init: Server.() -> Unit) {
        val server = Server()
        server.init()
        servers.add(server)
    }

    fun security(init: SecurityRequirement.() -> Unit) {
        val server = SecurityRequirement()
        server.init()
        security.add(server)
    }
}

open class PathItem(
        @field:JsonIgnore
        val path: Operation,
        @field:JsonIgnore
        val jsonKey: String
)

data class Paths(
        private val paths: MutableMap<String, MutableMap<String, Operation>> = HashMap()
) : MutableMap<String, MutableMap<String, Operation>> by paths {
    private fun makeOperation(init: Operation.() -> Unit): Operation {
        val apiPath = Operation()
        apiPath.init()
        return apiPath
    }

    private fun putPath(path: String, pathItem: PathItem) {
        if (containsKey(path)) {
            get(path)?.put(pathItem.jsonKey, pathItem.path)
        } else {
            put(path, mutableMapOf(pathItem.jsonKey to pathItem.path))
        }
    }

    fun get(path: String, init: Operation.() -> Unit) {
        putPath(path, PathItem(makeOperation(init), "get"))
    }

    fun put(path: String, init: Operation.() -> Unit) {
        putPath(path, PathItem(makeOperation(init), "put"))
    }

    fun post(path: String, init: Operation.() -> Unit) {
        putPath(path, PathItem(makeOperation(init), "post"))
    }

    fun delete(path: String, init: Operation.() -> Unit) {
        putPath(path, PathItem(makeOperation(init), "delete"))
    }

    fun patch(path: String, init: Operation.() -> Unit) {
        putPath(path, PathItem(makeOperation(init), "patch"))
    }

    fun head(path: String, init: Operation.() -> Unit) {
        putPath(path, PathItem(makeOperation(init), "head"))
    }

    fun options(path: String, init: Operation.() -> Unit) {
        putPath(path, PathItem(makeOperation(init), "options"))
    }

    fun trace(path: String, init: Operation.() -> Unit) {
        putPath(path, PathItem(makeOperation(init), "trace"))
    }
}

data class Components(
        val schemas: Map<String, Any> = emptyMap(),
        val securitySchemes: Map<String, SecurityScheme> = emptyMap()
)

data class ExternalDocumentation(
        var url: String,
        var description: String = ""
)

data class Tag(
        var name: String = "",
        var description: String = "",
        var externalDocs: ExternalDocumentation? = null
)

data class ServerVariable(
        var default: String,
        var enum: List<String> = emptyList(),
        var description: String = ""
)

data class Server(
        var url: String = "",
        var description: String = "",
        var variables: Map<String, ServerVariable> = emptyMap()
)

data class OpenApi(
        var openapi: String = "3.0.0",
        var info: Info = Info(),
        var paths: Paths = Paths(),
        var tags: List<Tag> = emptyList(),
        var externalDocs: ExternalDocumentation? = null,
        val servers: MutableList<Server> = mutableListOf(),
        val security: MutableList<SecurityRequirement> = mutableListOf()
) {
    companion object {
        @field:JsonIgnore
        val mapper = ObjectMapper()
    }

    init {
        val module = SimpleModule()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        module.addSerializer(Schema::class.java, SchemaSerializer())
        module.addSerializer(ParameterSchema::class.java, ParameterSchemaSerializer())
        module.addSerializer(Components::class.java, ComponentsSerializer())
        mapper.registerModule(module)
    }

    private val securitySchemes: MutableMap<String, SecurityScheme> = HashMap()

    val components: Components
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

            val schemas = responseSchemas
                    .plus(requestSchemas)
                    .plus(parameterSchemas)
            return Components(schemas, securitySchemes)
        }

    fun info(init: Info.() -> Unit) {
        info.init()
    }

    fun paths(init: Paths.() -> Unit) {
        paths.init()
    }

    fun server(init: Server.() -> Unit) {
        val server = Server()
        server.init()
        servers.add(server)
    }

    fun securityScheme(init: SecurityScheme.() -> Unit) {
        val security = SecurityScheme()
        security.init()
        securitySchemes.put(security.name, security)
    }

    fun security(init: SecurityRequirement.() -> Unit) {
        val securityReq = SecurityRequirement()
        securityReq.init()
        security.add(securityReq)
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

fun openapi3(init: OpenApi.() -> Unit): OpenApi {
    val openapi3 = OpenApi()
    openapi3.init()
    return openapi3
}
