package cc.vileda.openapi.dsl

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.*
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.servers.ServerVariable
import io.swagger.v3.oas.models.servers.ServerVariables
import io.swagger.v3.oas.models.tags.Tag
import io.swagger.v3.parser.OpenAPIV3Parser
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

fun openapiDsl(init: OpenAPI.() -> Unit): OpenAPI {
    return openapiDsl(OpenApiDslConfig(), init)
}

fun openapiDsl(config: OpenApiDslConfig, init: OpenAPI.() -> Unit): OpenAPI {
    return buildOpenApi(config, init)
}

private fun validatedJsonString(api: OpenAPI, pretty: Boolean): String {
    val mapper = Json.mapper()
    val json = if (pretty) {
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(api)
    } else {
        mapper.writeValueAsString(api)
    }
    OpenAPIV3Parser().read(toFile(json).absolutePath)
    return json
}

internal fun validatedJson(api: OpenAPI): JSONObject {
    return JSONObject(validatedJsonString(api, pretty = false))
}

fun OpenAPI.asJson(): JSONObject {
    return validatedJson(this)
}

fun OpenAPI.asJsonString(pretty: Boolean = false): String {
    return validatedJsonString(this, pretty)
}

fun OpenAPI.asFile(): File {
    return toFile(asJsonString(pretty = true))
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
    servers = servers ?: mutableListOf()
    servers.add(server)
}

fun OpenAPI.security(init: SecurityRequirement.() -> Unit) {
    val securityReq = SecurityRequirement()
    securityReq.init()
    security = security ?: mutableListOf()
    security.add(securityReq)
}

fun OpenAPI.tag(init: Tag.() -> Unit) {
    val tag = Tag()
    tag.init()
    tags = tags ?: mutableListOf()
    tags.add(tag)
}

fun OpenAPI.paths(init: Paths.() -> Unit) {
    paths = Paths()
    paths.init()
}

fun OpenAPI.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun PathItem.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun Operation.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun Components.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun ApiResponse.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun OpenAPI.components(init: Components.() -> Unit) {
    components = components ?: Components()
    components.init()
}

inline fun <reified T> Components.schema(init: Schema<*>.() -> Unit) {
    val resolved = resolveTypeSchemas(T::class.java)
        ?: throw IllegalArgumentException("could not resolve schema for ${T::class.qualifiedName}")
    resolved.root.init()
    addResolvedSchemas(resolved)
}

inline fun <reified T> Components.schema() {
    val resolved = resolveTypeSchemas(T::class.java)
        ?: throw IllegalArgumentException("could not resolve schema for ${T::class.qualifiedName}")
    addResolvedSchemas(resolved)
}

fun Components.securityScheme(name: String, init: SecurityScheme.() -> Unit) {
    addSecuritySchemes(name, SecurityScheme().apply(init))
}

fun SecurityScheme.flows(init: OAuthFlows.() -> Unit) {
    flows = OAuthFlows()
    flows.init()
}

fun OAuthFlows.password(init: OAuthFlow.() -> Unit) {
    password = OAuthFlow()
    password.init()
}

fun OAuthFlows.implicit(init: OAuthFlow.() -> Unit) {
    implicit = OAuthFlow()
    implicit.init()
}

fun OAuthFlows.clientCredentials(init: OAuthFlow.() -> Unit) {
    clientCredentials = OAuthFlow()
    clientCredentials.init()
}

fun OAuthFlows.authorizationCode(init: OAuthFlow.() -> Unit) {
    authorizationCode = OAuthFlow()
    authorizationCode.init()
}

fun OAuthFlow.scopes(init: Scopes.() -> Unit) {
    scopes = scopes ?: Scopes()
    scopes.init()
}

fun Scopes.scope(name: String, item: String) {
    addString(name, item)
}

fun OAuthFlow.scope(name: String, item: String) {
    scopes = scopes ?: Scopes()
    scopes.addString(name, item)
}

fun OAuthFlow.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun Tag.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun Paths.path(name: String, init: PathItem.() -> Unit) {
    val pathItem = PathItem()
    pathItem.init()
    addPathItem(name, pathItem)
}

fun Paths.extension(name: String, value: Any) {
    addExtension(name, value)
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
    parameters = parameters ?: mutableListOf()
    val parameter = Parameter()
    parameter.init()
    parameters.add(parameter)
}

fun ApiResponses.response(name: String, init: ApiResponse.() -> Unit) {
    val response = ApiResponse()
    response.init()
    addApiResponse(name, response)
}

fun ApiResponse.content(init: Content.() -> Unit) {
    content = Content()
    content.init()
}

fun RequestBody.content(init: Content.() -> Unit) {
    content = Content()
    content.init()
}

fun RequestBody.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun Parameter.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun ExternalDocumentation.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun Parameter.content(init: Content.() -> Unit) {
    content = Content()
    content.init()
}

inline fun <reified T> Parameter.schema(init: Schema<*>.() -> Unit) {
    schema = findSchema<T>()
    schema.init()
}

inline fun <reified T> Parameter.schema() {
    schema = findSchema<T>()
}

fun <T> Schema<T>.extension(name: String, value: Any) {
    addExtension(name, value)
}

inline fun <reified T> mediaType(): MediaType {
    val mediaType = MediaType()
    val modelSchema = findSchema<T>()
    mediaType.schema = modelSchema
    return mediaType
}

inline fun <reified T> mediaTypeRef(): MediaType {
    val referenceName = registerSchemaReference(T::class.java)
    val mediaType = MediaType()
    mediaType.schema = Schema<T>()
    mediaType.schema.`$ref` = referenceName
    return mediaType
}

inline fun <reified T> Content.mediaType(name: String, init: MediaType.() -> Unit) {
    val mediaType = mediaType<T>()
    mediaType.init()
    addMediaType(name, mediaType)
}

inline fun <reified T> Content.mediaTypeRef(name: String, init: MediaType.() -> Unit) {
    val mediaType = mediaTypeRef<T>()
    mediaType.init()
    addMediaType(name, mediaType)
}

inline fun <reified T> Content.mediaTypeRef(name: String) {
    val mediaType = mediaTypeRef<T>()
    addMediaType(name, mediaType)
}

inline fun <reified T> Content.mediaType(name: String) {
    val mediaType = mediaType<T>()
    addMediaType(name, mediaType)
}

inline fun <reified T> Content.mediaTypeArrayOfRef(name: String, init: MediaType.() -> Unit) {
    val mediaTypeArray = mediaType<List<*>>()
    val mediaTypeObj = mediaTypeRef<T>()
    mediaTypeArray.init()
    val arraySchema = mediaTypeArray.schema as ArraySchema
    arraySchema.items(mediaTypeObj.schema)
    addMediaType(name, mediaTypeArray)
}

inline fun <reified T> Content.mediaTypeArrayOf(name: String, init: MediaType.() -> Unit) {
    val mediaTypeArray = mediaType<List<*>>()
    val mediaTypeObj = mediaType<T>()
    mediaTypeArray.init()
    val arraySchema = mediaTypeArray.schema as ArraySchema
    arraySchema.items(mediaTypeObj.schema)
    addMediaType(name, mediaTypeArray)
}

inline fun <reified T> Content.mediaTypeArrayOfRef(name: String) {
    val mediaTypeArray = mediaType<List<*>>()
    val mediaTypeObj = mediaTypeRef<T>()
    val arraySchema = mediaTypeArray.schema as ArraySchema
    arraySchema.items(mediaTypeObj.schema)
    addMediaType(name, mediaTypeArray)
}

inline fun <reified T> Content.mediaTypeArrayOf(name: String) {
    val mediaTypeArray = mediaType<List<*>>()
    val mediaTypeObj = mediaType<T>()
    val arraySchema = mediaTypeArray.schema as ArraySchema
    arraySchema.items(mediaTypeObj.schema)
    addMediaType(name, mediaTypeArray)
}

inline fun <reified T> findSchema(): Schema<*>? {
    return resolveTypeSchemas(T::class.java)?.root
}

inline fun <reified T> getEnumSchema(): Schema<*>? {
    return enumSchema(T::class.java)
}

fun MediaType.extension(name: String, value: Any) {
    addExtension(name, value)
}

inline fun <reified T> MediaType.example(value: T, init: Example.() -> Unit) {
    examples = examples ?: mutableMapOf()

    val example = Example()
    example.value = value
    example.init()
    examples[T::class.java.simpleName] = example
}

fun Server.variables(init: ServerVariables.() -> Unit) {
    variables = ServerVariables()
    variables.init()
}

fun Server.extension(name: String, value: Any) {
    addExtension(name, value)
}

fun ServerVariables.variable(name: String, init: ServerVariable.() -> Unit) {
    val serverVariable = ServerVariable()
    serverVariable.init()
    addServerVariable(name, serverVariable)
}
