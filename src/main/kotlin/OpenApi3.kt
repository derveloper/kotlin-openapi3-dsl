
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.nio.file.Files

private val mapper = ObjectMapper()

data class OpenApi3Info(
        var title: String = "",
        var version: String = ""
)

interface OpenApi3MediaType {
    val schema: String
}

data class OpenApi3TypedMediaType<T>(
        @field:JsonIgnore
        val clazz: Class<T>
) : OpenApi3MediaType {
    override val schema: String
    @field:JsonIgnore
    val schemaJson: JSONObject

    init {
        val schemaGen = JsonSchemaGenerator(mapper)
        val s = schemaGen.generateSchema(clazz)
        val jsonSchema = JSONObject(mapper.writeValueAsString(s))
        jsonSchema.remove("id")
        schemaJson = JSONObject(mapOf("schema" to jsonSchema))
        schema = "#/components/schemas/${clazz.simpleName}"
    }

}

class OpenApi3MediaTypeSerializer(mt: Class<OpenApi3MediaType>? = null) : StdSerializer<OpenApi3MediaType>(mt) {
    override fun serialize(value: OpenApi3MediaType, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject(mapOf("schema" to mapOf("\$ref" to value.schema))).toString())
    }
}

class OpenApi3ComponentsSerializer(mt: Class<OpenApi3Components>? = null) : StdSerializer<OpenApi3Components>(mt) {
    override fun serialize(value: OpenApi3Components, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(JSONObject().put("schemas", value.schemas).toString())
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

data class OpenApi3Responses(
        private val responses: MutableMap<String, OpenApi3Response> = HashMap()
) : MutableMap<String, OpenApi3Response> by responses

data class OpenApi3Path(
        var description: String = "",
        var operationId: String = ""
) {
    val responses = OpenApi3Responses()
    fun code(code: String, init: OpenApi3Response.() -> Unit) {
        val response = OpenApi3Response()
        response.init()
        responses.put(code, response)
    }
}


open class OpenApi3MethodPath(
        @field:JsonIgnore
        val path: OpenApi3Path
)

data class OpenApi3GetPath(val get: OpenApi3Path) : OpenApi3MethodPath(get)
data class OpenApi3PostPath(val post: OpenApi3Path) : OpenApi3MethodPath(post)
data class OpenApi3PutPath(val put: OpenApi3Path) : OpenApi3MethodPath(put)
data class OpenApi3DeletePath(val delete: OpenApi3Path) : OpenApi3MethodPath(delete)
data class OpenApi3PatchPath(val patch: OpenApi3Path) : OpenApi3MethodPath(patch)
data class OpenApi3HeadPath(val head: OpenApi3Path) : OpenApi3MethodPath(head)
data class OpenApi3OptionsPath(val options: OpenApi3Path) : OpenApi3MethodPath(options)

data class OpenApi3Paths(
        private val paths: MutableMap<String, Any> = HashMap()
) : MutableMap<String, Any> by paths {
    fun get(path: String, init: OpenApi3Path.() -> Unit) {
        val apiPath = OpenApi3Path()
        apiPath.init()
        put(path, OpenApi3GetPath(apiPath))
    }

    fun put(path: String, init: OpenApi3Path.() -> Unit) {
        val apiPath = OpenApi3Path()
        apiPath.init()
        put(path, OpenApi3PutPath(apiPath))
    }

    fun post(path: String, init: OpenApi3Path.() -> Unit) {
        val apiPath = OpenApi3Path()
        apiPath.init()
        put(path, OpenApi3PostPath(apiPath))
    }

    fun delete(path: String, init: OpenApi3Path.() -> Unit) {
        val apiPath = OpenApi3Path()
        apiPath.init()
        put(path, OpenApi3DeletePath(apiPath))
    }

    fun patch(path: String, init: OpenApi3Path.() -> Unit) {
        val apiPath = OpenApi3Path()
        apiPath.init()
        put(path, OpenApi3PatchPath(apiPath))
    }

    fun head(path: String, init: OpenApi3Path.() -> Unit) {
        val apiPath = OpenApi3Path()
        apiPath.init()
        put(path, OpenApi3HeadPath(apiPath))
    }

    fun options(path: String, init: OpenApi3Path.() -> Unit) {
        val apiPath = OpenApi3Path()
        apiPath.init()
        put(path, OpenApi3OptionsPath(apiPath))
    }
}

data class OpenApi3Components(
        val schemas: MutableMap<String, Any> = HashMap()
)

data class OpenApi3(
        var openapi: String = "3.0.0",
        var info: OpenApi3Info = OpenApi3Info(),
        var paths: OpenApi3Paths = OpenApi3Paths()
) {
    init {
        val module = SimpleModule()
        module.addSerializer(OpenApi3MediaType::class.java, OpenApi3MediaTypeSerializer())
        module.addSerializer(OpenApi3Components::class.java, OpenApi3ComponentsSerializer())
        mapper.registerModule(module)
    }

    val components: OpenApi3Components
        get() {
            val components: MutableMap<String, Any> = paths
                    .map { it.value as OpenApi3MethodPath }
                    .flatMap { it.path.responses.values }
                    .flatMap { it.content.values }
                    .fold(mutableMapOf()) { m, o ->
                        m.put(o.clazz.simpleName, o.schemaJson.getJSONObject("schema"))
                        m
                    }
            return OpenApi3Components(components)
        }

    fun info(init: OpenApi3Info.() -> Unit) {
        info.init()
    }

    fun paths(init: OpenApi3Paths.() -> Unit) {
        paths.init()
    }

    fun asJson(): JSONObject {
        val writeValueAsString = mapper.writeValueAsString(this)
        return JSONObject(JSONObject(JSONTokener(writeValueAsString)).toString())
    }

    fun asFile(): File {
        val file = Files.createTempFile("openapi-", ".json").toFile()
        file.writeText(asJson().toString())
        file.deleteOnExit()
        return file
    }
}

fun openapi3(init: OpenApi3.() -> Unit): OpenApi3 {
    val openapi3 = OpenApi3()
    openapi3.init()
    return openapi3
}
