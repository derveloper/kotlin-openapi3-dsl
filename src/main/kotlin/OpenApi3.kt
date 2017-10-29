
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
import java.util.*
import kotlin.collections.HashMap

val mapper = ObjectMapper()

class OpenApi3Info {
    var title: String = ""
    var version: String = ""
}

interface OpenApi3MediaType {
    val schema: JSONObject
}

class OpenApi3TypedMediaType<T> : OpenApi3MediaType {
    override val schema: JSONObject
    val klass: Class<T>

    constructor(value: Class<T>) {
        val schemaGen = JsonSchemaGenerator(mapper)
        val s = schemaGen.generateSchema(value)
        val jsonSchema = JSONObject(mapper.writeValueAsString(s))
        jsonSchema.remove("id")
        schema = JSONObject(mapOf("schema" to jsonSchema))
        klass = value
    }
}

class OpenApi3MediaTypeSerializer(mt: Class<OpenApi3MediaType>? = null) : StdSerializer<OpenApi3MediaType>(mt) {
    override fun serialize(value: OpenApi3MediaType, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeRawValue(value.schema.toString())
    }

}

class OpenApi3Response {
    var description: String = ""
    val content = HashMap<String, OpenApi3TypedMediaType<*>>()
    inline fun <reified T> response(mediaType: String) {
        val apiMediaType = OpenApi3TypedMediaType(T::class.java)
        content.put(mediaType, apiMediaType)
    }
}

class OpenApi3Responses : HashMap<String, OpenApi3Response>()

class OpenApi3Path {
    var description: String = ""
    var operationId: String = ""
    val responses = OpenApi3Responses()
    fun code(code: String, init: OpenApi3Response.() -> Unit) {
        val response = OpenApi3Response()
        response.init()
        responses.put(code, response)
    }
}

data class OpenApi3GetPath(
        val get: OpenApi3Path
)
class OpenApi3PostPath(val post: OpenApi3Path)
class OpenApi3PutPath(val put: OpenApi3Path)
class OpenApi3DeletePath(val delete: OpenApi3Path)

class OpenApi3PatchPath(val patch: OpenApi3Path)

class OpenApi3Paths : HashMap<String, Any>() {
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
}

data class Response(
        val code: Int,
        val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Response

        if (code != other.code) return false
        if (!Arrays.equals(body, other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code
        result = 31 * result + Arrays.hashCode(body)
        return result
    }
}

class OpenApi3Components(
        val schemas: MutableMap<String, Any> = HashMap()
)

class OpenApi3 {
    init {
        val module = SimpleModule()
        module.addSerializer(OpenApi3MediaType::class.java, OpenApi3MediaTypeSerializer())
        mapper.registerModule(module)
    }

    var openapi: String = "3.0.0"
    var info = OpenApi3Info()
    var paths = OpenApi3Paths()
    var components = OpenApi3Components()
    fun info(init: OpenApi3Info.() -> Unit) {
        info.init()
    }

    fun paths(init: OpenApi3Paths.() -> Unit) {
        paths.init()
    }

    fun asJson(): JSONObject {
        val writeValueAsString = mapper.writeValueAsString(this)
        return JSONObject(JSONTokener(writeValueAsString))
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
