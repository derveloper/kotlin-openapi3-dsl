package cc.vileda.openapi.dsl

import com.reprezen.kaizen.oasparser.OpenApi3Parser
import io.swagger.converter.ModelConverters
import io.swagger.oas.models.*
import io.swagger.oas.models.examples.Example
import io.swagger.oas.models.info.Info
import io.swagger.oas.models.media.Content
import io.swagger.oas.models.media.MediaType
import io.swagger.oas.models.media.Schema
import io.swagger.oas.models.parameters.Parameter
import io.swagger.oas.models.parameters.RequestBody
import io.swagger.oas.models.responses.ApiResponse
import io.swagger.oas.models.responses.ApiResponses
import io.swagger.oas.models.security.SecurityRequirement
import io.swagger.oas.models.security.SecurityScheme
import io.swagger.oas.models.servers.Server
import io.swagger.oas.models.servers.ServerVariable
import io.swagger.oas.models.servers.ServerVariables
import io.swagger.oas.models.tags.Tag
import io.swagger.util.Json
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

fun openapiDsl(init: OpenAPI.() -> Unit): OpenAPI {
    val openapi3 = OpenAPI()
    openapi3.init()
    return openapi3
}

internal fun validatedJson(api: OpenAPI): JSONObject {
    val json = Json.mapper().writeValueAsString(api)
    OpenApi3Parser().parse(toFile(json), true)
    return JSONObject(json)
}

fun OpenAPI.asJson(): JSONObject {
    return validatedJson(this)
}

fun OpenAPI.asFile(): File {
    return toFile(asJson().toString(2))
}

private fun toFile(json: String): File {
    val file = Files.createTempFile("openapi-", ".json").toFile()
    file.writeText(json)
    file.deleteOnExit()
    return file
}

fun OpenAPI.info(init: Info.() -> Unit) {
    info = Info()
    info.init()
}

fun OpenAPI.externalDocs(init: ExternalDocumentation.() -> Unit) {
    externalDocs = ExternalDocumentation()
    externalDocs.init()
}

fun OpenAPI.server(init: Server.() -> Unit) {
    val server = Server()
    server.init()
    if (servers == null)
        servers = mutableListOf()
    servers.add(server)
}

fun OpenAPI.security(init: SecurityRequirement.() -> Unit) {
    val securityReq = SecurityRequirement()
    securityReq.init()
    if (security == null)
        security = mutableListOf()
    security.add(securityReq)
}

fun OpenAPI.tag(init: Tag.() -> Unit) {
    val tag = Tag()
    tag.init()
    if (tags == null)
        tags = mutableListOf()
    tags.add(tag)
}

fun OpenAPI.paths(init: Paths.() -> Unit) {
    paths = Paths()
    paths.init()
}

fun OpenAPI.extensions(init: MutableMap<String, Any>.() -> Unit) {
    if (extensions == null)
        extensions = mutableMapOf()
    extensions.init()
}

fun OpenAPI.components(init: Components.() -> Unit) {
    if (components == null)
        components = Components()
    components.init()
}

fun Components.securityScheme(init: SecurityScheme.() -> Unit) {
    val security = SecurityScheme()
    security.init()
    if (securitySchemes == null) {
        securitySchemes = mutableMapOf()
    }
    securitySchemes.put(security.name, security)
}

fun Paths.path(name: String, init: PathItem.() -> Unit) {
    val pathItem = PathItem()
    pathItem.init()
    addPathItem(name, pathItem)
}

fun PathItem.get(init: Operation.() -> Unit) {
    get = Operation()
    get.init()
}

fun PathItem.put(init: Operation.() -> Unit) {
    put = Operation()
    put.init()
}

fun PathItem.post(init: Operation.() -> Unit) {
    post = Operation()
    post.init()
}

fun PathItem.delete(init: Operation.() -> Unit) {
    delete = Operation()
    delete.init()
}

fun PathItem.patch(init: Operation.() -> Unit) {
    patch = Operation()
    patch.init()
}

fun PathItem.options(init: Operation.() -> Unit) {
    options = Operation()
    options.init()
}

fun PathItem.head(init: Operation.() -> Unit) {
    head = Operation()
    head.init()
}

fun PathItem.trace(init: Operation.() -> Unit) {
    trace = Operation()
    trace.init()
}

fun Operation.responses(init: ApiResponses.() -> Unit) {
    responses = ApiResponses()
    responses.init()
}

fun Operation.requestBody(init: RequestBody.() -> Unit) {
    requestBody = RequestBody()
    requestBody.init()
}

fun Operation.parameter(init: Parameter.() -> Unit) {
    if (parameters == null) {
        parameters = mutableListOf()
    }
    val parameter = Parameter()
    parameter.init()
    parameters.add(parameter)
}

fun ApiResponses.response(name: String, init: ApiResponse.() -> Unit) {
    val response = ApiResponse()
    response.init()
    put(name, response)
}

fun ApiResponse.content(init: Content.() -> Unit) {
    content = Content()
    content.init()
}

fun RequestBody.content(init: Content.() -> Unit) {
    content = Content()
    content.init()
}

fun Parameter.content(init: Content.() -> Unit) {
    content = Content()
    content.init()
}

inline fun <reified T> Parameter.schema(init: Schema<*>.() -> Unit) {
    schema = ModelConverters.getInstance().read(T::class.java)[T::class.java.simpleName]
    schema.init()
}

inline fun <reified T> Parameter.schema() {
    schema<T> { /* noop */ }
}

inline fun <reified T> Content.mediaType(name: String, init: MediaType.() -> Unit) {
    val mediaType = MediaType()
    mediaType.init()
    val modelSchema = ModelConverters.getInstance().read(T::class.java)
    mediaType.schema = modelSchema[T::class.java.simpleName]
    addMediaType(name, mediaType)
}

inline fun <reified T> Content.mediaType(name: String) {
    mediaType<T>(name) { /* noop */ }
}

inline fun <reified T> MediaType.example(value: T, init: Example.() -> Unit) {
    if (examples == null) {
        examples = mutableMapOf()
    }

    val example = Example()
    example.value = value
    example.init()
    examples.put(T::class.java.simpleName, example)
}

fun Server.variables(init: ServerVariables.() -> Unit) {
    variables = ServerVariables()
    variables.init()
}

fun ServerVariables.variable(name: String, init: ServerVariable.() -> Unit) {
    val serverVariable = ServerVariable()
    serverVariable.init()
    addServerVariable(name, serverVariable)
}
