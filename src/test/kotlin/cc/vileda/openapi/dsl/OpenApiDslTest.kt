package cc.vileda.openapi.dsl

import com.reprezen.kaizen.oasparser.OpenApi3Parser
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.swagger.oas.models.parameters.Parameter
import io.swagger.oas.models.security.SecurityScheme


data class ExampleSchema(val foo: String)
data class AnotherExampleSchema(val bar: String)
data class ExampleRequestSchema(val foo: String)

class OpenApi3BuilderTest : StringSpec() {
    init {
        val api = openapiDsl {
            openapi = "3.0.0"
            server {
                url = "http://localhost"
                description = "localhost server"
                variables {
                    variable("foo") {
                        addEnumItem("bar")
                    }
                }
            }
            components {
                securityScheme {
                    name = "foo"
                    type = SecurityScheme.Type.OPENIDCONNECT
                    openIdConnectUrl = "http://localhost/auth"
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
                    post {
                        description = "test"
                        tags = listOf("tag1")
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
                                mediaType<ExampleSchema>("application/json")
                            }
                        }
                        requestBody {
                            description = "example request"
                            required = true
                            content {
                                mediaType<ExampleRequestSchema>("application/json") {
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
                                    mediaType<ExampleSchema>("application/json") {
                                        description = "bar"
                                        example(ExampleSchema("bar")) {
                                            description = "example schema value"
                                        }
                                        example(AnotherExampleSchema("foo")) {
                                            description = "another example schema value"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        "builder should accept openapi fields" {
            api shouldNotBe null
            api.openapi shouldBe "3.0.0"
            api.info.title shouldBe "jjjj"
            api.info.version shouldBe "1.0"
        }

        "openapi should render as valid json spec" {
            val asJson = api.asJson()
            println(asJson.toString(2))
            val parsed = OpenApi3Parser().parse(api.asFile())
            parsed.validationItems.size shouldBe 0
        }
    }
}