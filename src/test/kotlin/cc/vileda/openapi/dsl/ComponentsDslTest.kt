package cc.vileda.openapi.dsl

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.swagger.v3.oas.models.PathItem

class ComponentsDslTest : StringSpec() {
    init {
        val api = openapiDsl {
            openapi = "3.0.0"
            info { title = "components-test"; version = "1.0" }

            components {
                parameter("pageSize") {
                    name = "pageSize"
                    `in` = "query"
                    description = "number of results per page"
                }
                response("NotFound") {
                    description = "resource not found"
                }
                requestBody("ItemBody") {
                    description = "item payload"
                    required = true
                }
                example("fooExample") {
                    summary = "a foo example"
                    value = "foo"
                }
                header("RateLimit") {
                    description = "calls remaining per hour"
                }
                link("UserLink") {
                    operationId = "getUser"
                    description = "linked user"
                }
                callback("onEvent") {
                }
            }

            paths {
                path("/items") {
                    get {
                        parameterRef("pageSize")
                        requestBodyRef("ItemBody")
                        callbackRef("onEvent")
                        responses {
                            responseRef("404", "NotFound")
                            response("200") {
                                description = "OK"
                                headerRef("RateLimit")
                                linkRef("UserLink")
                                content {
                                    mediaTypeRef<String>("application/json") {
                                        exampleRef("fooExample")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val getOp = api.paths["/items"]!!
            .readOperationsMap()!![PathItem.HttpMethod.GET]!!

        "Components.parameter registers a named parameter entry" {
            val p = api.components.parameters["pageSize"]
            p shouldNotBe null
            p!!.description shouldBe "number of results per page"
        }

        "Components.response registers a named response entry" {
            val r = api.components.responses["NotFound"]
            r shouldNotBe null
            r!!.description shouldBe "resource not found"
        }

        "Components.requestBody registers a named requestBody entry" {
            val rb = api.components.requestBodies["ItemBody"]
            rb shouldNotBe null
            rb!!.description shouldBe "item payload"
        }

        "Components.example registers a named example entry" {
            val ex = api.components.examples["fooExample"]
            ex shouldNotBe null
            ex!!.summary shouldBe "a foo example"
        }

        "Components.header registers a named header entry" {
            val h = api.components.headers["RateLimit"]
            h shouldNotBe null
            h!!.description shouldBe "calls remaining per hour"
        }

        "Components.link registers a named link entry" {
            val lnk = api.components.links["UserLink"]
            lnk shouldNotBe null
            lnk!!.operationId shouldBe "getUser"
        }

        "Components.callback registers a named callback entry" {
            api.components.callbacks shouldNotBe null
            api.components.callbacks["onEvent"] shouldNotBe null
        }

        "parameterRef emits canonical #/components/parameters/<name> ref" {
            val ref = getOp.parameters.first().`$ref`
            ref shouldBe "#/components/parameters/pageSize"
        }

        "responseRef emits canonical #/components/responses/<name> ref" {
            val ref = getOp.responses["404"]!!.`$ref`
            ref shouldBe "#/components/responses/NotFound"
        }

        "requestBodyRef emits canonical #/components/requestBodies/<name> ref" {
            val ref = getOp.requestBody.`$ref`
            ref shouldBe "#/components/requestBodies/ItemBody"
        }

        "exampleRef in MediaType emits canonical #/components/examples/<name> ref" {
            val ref = getOp.responses["200"]!!
                .content!!["application/json"]!!
                .examples!!["fooExample"]!!
                .`$ref`
            ref shouldBe "#/components/examples/fooExample"
        }

        "headerRef in ApiResponse emits canonical #/components/headers/<name> ref" {
            val ref = getOp.responses["200"]!!
                .headers!!["RateLimit"]!!
                .`$ref`
            ref shouldBe "#/components/headers/RateLimit"
        }

        "linkRef in ApiResponse emits canonical #/components/links/<name> ref" {
            val ref = getOp.responses["200"]!!
                .links!!["UserLink"]!!
                .`$ref`
            ref shouldBe "#/components/links/UserLink"
        }

        "callbackRef on Operation emits canonical #/components/callbacks/<name> ref" {
            val ref = getOp.callbacks!!["onEvent"]!!.`$ref`
            ref shouldBe "#/components/callbacks/onEvent"
        }

        "doc with all seven component categories serialises to JSON with a components object" {
            val json = validatedJson(api)
            json.has("components") shouldBe true
            val comps = json.getJSONObject("components")
            comps.has("parameters") shouldBe true
            comps.has("responses") shouldBe true
            comps.has("requestBodies") shouldBe true
            comps.has("examples") shouldBe true
            comps.has("headers") shouldBe true
            comps.has("links") shouldBe true
            comps.has("callbacks") shouldBe true
        }
    }
}
