package cc.vileda.openapi.dsl

import io.kotlintest.matchers.instanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.responses.ApiResponse
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


data class ExampleSchema(val foo: String)
data class AnotherExampleSchema(val bar: String)
data class ExampleRequestSchema(val foo: String)
data class ListExampleSchema(val baz: List<ExampleSchema>)

@Suppress("unused")
enum class ExampleEnum {
    ONE,
    TWO
}

class OpenApi3BuilderTest : StringSpec() {
    init {
        val api = openapiDsl {
            openapi = "3.0.0"
            tag {
                name = "example_tag"
                description = "an example tag"
                extension("x-beta", true)
            }
            externalDocs {
                description = "developer hub"
                url = "http://hub.api"
            }
            server {
                url = "http://localhost"
                description = "localhost server"
                variables {
                    variable("foo") {
                        addEnumItem("bar")
                        extension("x-foo", "bar")
                    }
                }
            }
            extension("x-stable", true)
            extension("x-internal", true)
            components {
                schema<ExampleSchema>()
                schema<ExampleRequestSchema>()
                schema<AnotherExampleSchema>()
                schema<ListExampleSchema>()
                schema<ExampleEnum>()
                securityScheme("bearer") {
                    type = SecurityScheme.Type.OPENIDCONNECT
                    openIdConnectUrl = "http://localhost:8080/auth"
                    flows {
                        implicit {
                            authorizationUrl = "http://localhost:8080/auth"
                            refreshUrl = "http://localhost:8080/token"
                            tokenUrl = "http://localhost:8080/token"
                            extension("x-internal", true)
                            scopes {
                                scope("foo", "foo:read")
                            }
                        }
                    }
                }
            }
            security {
                put(SecurityScheme.Type.OPENIDCONNECT.toString(), listOf("bar"))
            }
            info {
                title = "jjjj"
                version = "1.0"
            }
            paths {
                path("foo") {
                    extension("x-experimental", true)
                    post {
                        description = "test"
                        tags = listOf("tag1")
                        extension("x-version", "3.0")
                        parameter {
                            name = "id"
                            `in` = "path"
                            style = Parameter.StyleEnum.SIMPLE
                            schema<String>()
                        }
                        parameter {
                            name = "name"
                            `in` = "query"
                            style = Parameter.StyleEnum.SIMPLE
                            required = true
                            schema<String>()
                        }
                        parameter {
                            name = "firstname"
                            `in` = "query"
                            style = Parameter.StyleEnum.DEEPOBJECT
                            required = false
                            deprecated = true
                            content {
                                mediaTypeRef<ExampleSchema>("application/json")
                            }
                        }
                        requestBody {
                            description = "example request"
                            required = true
                            content {
                                mediaTypeRef<ExampleRequestSchema>("application/json") {
                                    description = "request schema"
                                    example(ExampleRequestSchema("bar")) {
                                        description = "example schema value"
                                    }
                                }
                            }
                        }
                        responses {
                            response("foo") {
                                description = "bar"
                                content {
                                    mediaTypeRef<AnotherExampleSchema>("application/json") {
                                        description = "bar"
                                        example(AnotherExampleSchema("bar")) {
                                            description = "example schema value"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    get {
                        responses {
                            response("200") {
                                content {
                                    mediaTypeArrayOfRef<ExampleSchema>("application/json") {
                                        example(ExampleSchema("foo")) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        "builder should set openapi fields" {
            api shouldNotBe null
            api.openapi shouldBe "3.0.0"
            api.info.title shouldBe "jjjj"
            api.info.version shouldBe "1.0"
            val securityScheme =
                    api.components.securitySchemes["bearer"]
            securityScheme shouldNotBe null
            securityScheme!!.flows!!.implicit shouldNotBe null
            securityScheme.flows.implicit!!.extensions!!["x-internal"] shouldBe true
            securityScheme.type shouldBe SecurityScheme.Type.OPENIDCONNECT

            val postOp = api.paths["foo"]!!.readOperationsMap()!![PathItem.HttpMethod.POST]
            postOp shouldNotBe null
            postOp!!.extensions!!["x-version"] shouldBe "3.0"
            postOp.responses["foo"]!!.description shouldBe "bar"
            postOp.responses["foo"]!!.content!!["application/json"]!!.schema.javaClass shouldBe Schema<AnotherExampleSchema>().javaClass
        }

        "openapi should render as valid json spec" {
            val asJson = validatedJson(api)
            println(asJson.toString(2))
        }

        "findSchema should return valid schema" {
            findSchema<String>() shouldBe StringSchema()
            findSchema<Int>() shouldBe IntegerSchema()
            findSchema<Boolean>() shouldBe BooleanSchema()
            findSchema<Long>() shouldBe IntegerSchema().format("int64")
            findSchema<BigDecimal>() shouldBe IntegerSchema().format("")
            findSchema<Date>() shouldBe DateSchema()
            findSchema<LocalDate>() shouldBe DateSchema()
            findSchema<LocalDateTime>() shouldBe DateTimeSchema()
            findSchema<ExampleRequestSchema>()?.javaClass shouldBe Schema<ExampleRequestSchema>().javaClass
        }

        "findSchema should return a valid schema for an enum" {
            val schema = findSchema<ExampleEnum>()
            schema shouldNotBe null
            schema?.type shouldBe "string"
            schema?.enum shouldBe listOf("ONE", "TWO")
        }

        "findSchema should return a valid schema for a list" {
            val schema = api.paths["foo"]!!.readOperationsMap()!![PathItem.HttpMethod.GET]!!.responses["200"]!!.content!!["application/json"]!!.schema
            schema.type shouldBe "array"
            schema shouldBe instanceOf(ArraySchema()::class)
            (schema as ArraySchema).items.`$ref` shouldBe "#/components/schemas/ExampleSchema"
        }

        val standardResponses = linkedMapOf(
            "400" to ApiResponse().apply { description = "Bad Request" },
            "401" to ApiResponse().apply { description = "Unauthorized" },
            "500" to ApiResponse().apply { description = "Internal Server Error" }
        )
        val orderingApi = openapiDsl {
            info { title = "ordering-test"; version = "1.0" }
            paths {
                path("/items") {
                    get {
                        responses {
                            response("204") { description = "No Content" }
                            putAll(standardResponses)
                        }
                    }
                }
            }
        }

        // Regression for issue #146: JSONObject scrambles key order; Jackson must preserve it
        "asJsonString should preserve 204-first insertion order when putAll adds shared responses" {
            val json = orderingApi.asJsonString()
            val idx204 = json.indexOf("\"204\"")
            val idx400 = json.indexOf("\"400\"")
            val idx401 = json.indexOf("\"401\"")
            val idx500 = json.indexOf("\"500\"")
            (idx204 in 0 until idx400) shouldBe true
            (idx400 < idx401) shouldBe true
            (idx401 < idx500) shouldBe true
        }

        "asFile should preserve 204-first insertion order when putAll adds shared responses" {
            val content = orderingApi.asFile().readText()
            val idx204 = content.indexOf("\"204\"")
            val idx400 = content.indexOf("\"400\"")
            val idx401 = content.indexOf("\"401\"")
            val idx500 = content.indexOf("\"500\"")
            (idx204 in 0 until idx400) shouldBe true
            (idx400 < idx401) shouldBe true
            (idx401 < idx500) shouldBe true
        }

        "asFile pretty output is valid parseable JSON" {
            val content = orderingApi.asFile().readText()
            val parsed = JSONObject(content)
            parsed.has("openapi") shouldBe true
            parsed.has("paths") shouldBe true
        }
    }
}