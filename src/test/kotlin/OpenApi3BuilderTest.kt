
import com.reprezen.kaizen.oasparser.OpenApi3Parser
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import org.json.JSONObject

data class ExampleSchema(val foo: String)
data class AnotherExampleSchema(val bar: String)

class OpenApi3BuilderTest : StringSpec() {
    init {
        val api = openapi3 {
            openapi = "3.0.0"
            info {
                title = "jjjj"
                version = "1.0"
            }
            paths {
                get("/foo") {
                    description = "fooo"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                put("/bar") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                post("/baz") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<AnotherExampleSchema>("application/json")
                    }
                }
                delete("/del") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                patch("/patch") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                head("/head") {
                    description = "bar"
                    code("200") {
                        description = "some response"
                        response<ExampleSchema>("application/json")
                    }
                }
                options("/options") {
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
            val openApi3GetPath = api.paths["/foo"] as OpenApi3GetPath
            openApi3GetPath.get.description shouldBe "fooo"
            val openApi3Response = openApi3GetPath.get.responses["200"] as OpenApi3Response
            val openApi3MediaType = openApi3Response.content["application/json"]
            openApi3MediaType?.schemaJson?.getJSONObject("schema")?.getString("type") shouldBe "object"
            api.components.schemas.values.size shouldBe 2
        }

        "openapi should convert to valid openapi3 spec" {
            val file = api.asFile()
            //println(json.toString(2))
            val parse = OpenApi3Parser().parse(file, false)
            parse.validate()
            println(JSONObject(parse.toJson().toString()).toString(4))
            parse.validationItems.size shouldBe 0
            parse.info.title shouldBe "jjjj"
            parse.info.version shouldBe "1.0"
        }

    }
}