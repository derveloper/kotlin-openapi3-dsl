package cc.vileda.openapi.dsl

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.swagger.oas.models.PathItem
import io.swagger.oas.models.media.BooleanSchema
import io.swagger.oas.models.media.IntegerSchema
import io.swagger.oas.models.media.Schema
import io.swagger.oas.models.media.StringSchema
import io.swagger.oas.models.parameters.Parameter
import io.swagger.oas.models.security.SecurityScheme


data class ExampleSchema(val foo: String)
data class AnotherExampleSchema(val bar: String)
data class ExampleRequestSchema(val foo: String)

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
                securityScheme {
                    name = "foo"
                    type = SecurityScheme.Type.OPENIDCONNECT
                    openIdConnectUrl = "http://localhost/auth"
                    flows {
                        implicit {
                            authorizationUrl = "http://localhost:8080/auth"
                            refreshUrl = "http://localhost:8080/token"
                            tokenUrl = "http://localhost:8080/token"
                            extension("x-internal", true)
                            scopes {
                                extension("x-internal", false)
                                scope("foo", "foo:read")
                            }
                        }
                    }
                }
            }
            security {
                put("foo", listOf("bar"))
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
                }
            }
        }

        "builder should set openapi fields" {
            api shouldNotBe null
            api.openapi shouldBe "3.0.0"
            api.info.title shouldBe "jjjj"
            api.info.version shouldBe "1.0"
            val securityScheme = api.components.securitySchemes["foo"]
            securityScheme shouldNotBe null
            securityScheme!!.flows!!.implicit shouldNotBe null
            securityScheme.flows.implicit!!.extensions!!["x-internal"] shouldBe true
            securityScheme.flows.implicit.scopes!!.extensions!!["x-internal"] shouldBe false
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
            findSchema<ExampleRequestSchema>()?.javaClass shouldBe Schema<ExampleRequestSchema>().javaClass
        }
    }
}