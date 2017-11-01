import cc.vileda.openapi3.*
import cc.vileda.openapi3.OpenApi.Companion.mapper
import com.reprezen.kaizen.oasparser.OpenApi3Parser
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import org.json.JSONObject

data class ExampleSchema(val foo: String)
data class AnotherExampleSchema(val bar: String)
data class ExampleRequestSchema(val foo: String)

class OpenApi3BuilderTest : StringSpec() {
    init {
        val api = openapi3 {
            openapi = "3.0.0"
            server {
                url = "http://localhost"
                description = "localhost server"
                variables = mapOf("foo" to ServerVariable("bar"))
            }
            securityScheme {
                name = "foo"
                type = SecurityScheme.Type.OPEN_ID_CONNECT
                openIdConnectUrl = "http://localhost/auth"
                flows {
                    implicit = OAuthFlow("", "", emptyMap())
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
                get("/path") {
                    tags = listOf("foo", "bar")
                    description = "fooo"
                    server {
                        url = "http://localhost"
                        description = "localhost server"
                        variables = mapOf("foo" to ServerVariable("bar"))
                    }
                    ok {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                get("/path/{id}") {
                    description = "fooo"
                    parameter {
                        description = "the id"
                        name = "id"
                        schema<String>()
                    }
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                put("/path") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                post("/path") {
                    description = "bar"
                    requestBody {
                        description = "example request"
                        request<ExampleRequestSchema>("application/json")
                        request<ExampleRequestSchema>("application/xml")
                    }
                    created {
                        description = "some response"
                        response<AnotherExampleSchema>("application/json")
                    }
                }
                delete("/path") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                patch("/path") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                head("/path") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                options("/path") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                options("/path2") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                post("/path2") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                trace("/path2") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
            }
        }

        "builder should accept openapi fields" {
            api shouldNotBe null
            api.openapi shouldBe "3.0.0"
            api.info.title shouldBe "jjjj"
            api.info.version shouldBe "1.0"
            val openApi3GetPath = api.paths["/path"]?.get("get") as Operation
            //val openApi3PostPath = api.paths["/path"] as OpenApi3PostPath
            openApi3GetPath.description shouldBe "fooo"
            val openApi3Response = openApi3GetPath.responses["200"] as Response
            //val openApi3Requests = openApi3PostPath.post.requestBody
            //openApi3Requests shouldNotBe null
            //openApi3Requests!!.description shouldBe "example request"
            //openApi3Requests.values.size shouldBe 2
            val openApi3MediaType = openApi3Response.content["application/json"]
            openApi3MediaType?.schemaJson?.getJSONObject("schema")?.getString("type") shouldBe "object"
            api.components.schemas?.values?.size shouldBe 4
        }

        "openapi should convert to valid openapi3 spec" {
            val file = api.asFile()
            println(api.asJson().toString(2))
            val parse = OpenApi3Parser().parse(file, false)
            parse.validate()
            parse.validationItems.forEach { println(it.msg) }
            parse.validationItems.size shouldBe 0
            parse.info.title shouldBe "jjjj"
            parse.info.version shouldBe "1.0"
        }

        "openapi components object should convert to json" {
            val securityScheme = SecurityScheme(name = "bar")
            println(mapper.writeValueAsString(securityScheme))
            val securitySchemes = mapOf<String?, SecurityScheme>(
                    "foo" to securityScheme
            )
            println(mapper.writeValueAsString(securitySchemes))
        }

        "openapi object converts to json" {
            println(JSONObject(mapper.writeValueAsString(api)).toString(2))
        }
    }
}