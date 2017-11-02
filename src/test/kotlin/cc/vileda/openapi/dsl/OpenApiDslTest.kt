package cc.vileda.openapi.dsl

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.swagger.oas.models.security.SecurityScheme
import io.swagger.util.Json
import org.json.JSONObject


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
                    get {
                        description = "test"
                        tags = listOf("tag1")
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

        "openapi should render as json" {
            val stringSpec = Json.mapper().writeValueAsString(api)
            println(JSONObject(stringSpec).toString(2))
        }
    }
}