
import com.reprezen.kaizen.oasparser.OpenApi3Parser
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

data class ExampleSchema(val foo: String)

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
                }
                post("/baz") {
                    description = "bar"
                }
                delete("/del") {
                    description = "bar"
                }
                patch("/patch") {
                    description = "bar"
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
            openApi3MediaType?.schema?.getJSONObject("schema")?.getString("type") shouldBe "object"
        }

        "openapi should convert to valid openapi3 spec" {
            val json = api.asJson()
            val file = api.asFile()
            println(json.toString(2))
            val parse = OpenApi3Parser().parse(file, false)
            parse.validate()
            parse.validationItems.size shouldBe 0
            parse.info.title shouldBe "jjjj"
            parse.info.version shouldBe "1.0"
        }

    }
}