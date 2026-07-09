package cc.vileda.openapi.dsl

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.ArraySchema
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

data class RegistryLeaf(val value: String)
data class RegistryBranch(val leaf: RegistryLeaf, val count: Int)
data class RegistryRoot(val branch: RegistryBranch, val label: String)

data class IsolatedNode(val code: String)

@io.swagger.v3.oas.annotations.media.Schema(name = "RenamedWidget")
data class AnnotatedWidget(val id: Int)

/** Builds an [OpenAPI] with a single GET /t -> 200 response using [mediaTypeRef]. */
private inline fun <reified T : Any> apiWithMediaTypeRef(): OpenAPI = openapiDsl {
    info { title = "t"; version = "1.0" }
    paths {
        path("/t") {
            get {
                responses {
                    response("200") {
                        content {
                            mediaTypeRef<T>("application/json")
                        }
                    }
                }
            }
        }
    }
}

class SchemaRegistryTest : StringSpec() {
    init {
        "mediaTypeRef without components block registers root and all transitive types" {
            val schemas = apiWithMediaTypeRef<RegistryRoot>().components?.schemas
            schemas shouldNotBe null
            schemas!!["RegistryRoot"] shouldNotBe null
            schemas["RegistryBranch"] shouldNotBe null
            schemas["RegistryLeaf"] shouldNotBe null
        }

        "mediaTypeRef schema carries canonical dollar-ref pointing to root component key" {
            val schema = apiWithMediaTypeRef<RegistryRoot>()
                .paths["/t"]!!
                .readOperationsMap()!![PathItem.HttpMethod.GET]!!
                .responses["200"]!!.content!!["application/json"]!!.schema
            schema.`$ref` shouldBe "#/components/schemas/RegistryRoot"
        }

        "mediaTypeArrayOfRef registers all transitive types and emits array schema with ref items" {
            val api = openapiDsl {
                info { title = "t"; version = "1.0" }
                paths {
                    path("/t") {
                        get {
                            responses {
                                response("200") {
                                    content {
                                        mediaTypeArrayOfRef<RegistryRoot>("application/json")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val schemas = api.components?.schemas
            schemas shouldNotBe null
            schemas!!["RegistryRoot"] shouldNotBe null
            schemas["RegistryBranch"] shouldNotBe null
            schemas["RegistryLeaf"] shouldNotBe null
            val outerSchema = api.paths["/t"]!!
                .readOperationsMap()!![PathItem.HttpMethod.GET]!!
                .responses["200"]!!.content!!["application/json"]!!.schema
            outerSchema.type shouldBe "array"
            (outerSchema as ArraySchema).items.`$ref` shouldBe "#/components/schemas/RegistryRoot"
        }

        "components schema block registers root and all transitive types" {
            val schemas = openapiDsl {
                info { title = "t"; version = "1.0" }
                components { schema<RegistryRoot>() }
            }.components?.schemas
            schemas shouldNotBe null
            schemas!!["RegistryRoot"] shouldNotBe null
            schemas["RegistryBranch"] shouldNotBe null
            schemas["RegistryLeaf"] shouldNotBe null
        }

        "explicitly customised root schema is not overwritten by auto-commit" {
            val api = openapiDsl {
                info { title = "t"; version = "1.0" }
                components {
                    schema<RegistryRoot> { description = "intentional custom description" }
                }
                paths {
                    path("/t") {
                        get {
                            responses {
                                response("200") {
                                    content { mediaTypeRef<RegistryRoot>("application/json") }
                                }
                            }
                        }
                    }
                }
            }
            api.components?.schemas?.get("RegistryRoot")?.description shouldBe "intentional custom description"
        }

        "second schema call for the same type does not overwrite prior explicit root customization" {
            val api = openapiDsl {
                info { title = "t"; version = "1.0" }
                components {
                    schema<RegistryRoot> { description = "pinned description" }
                    schema<RegistryRoot>()
                }
            }
            api.components?.schemas?.get("RegistryRoot")?.description shouldBe "pinned description"
        }

        "Schema name annotation determines the component key and the dollar-ref value" {
            val api = apiWithMediaTypeRef<AnnotatedWidget>()
            api.components?.schemas?.get("RenamedWidget") shouldNotBe null
            api.components?.schemas?.get("AnnotatedWidget") shouldBe null
            api.paths["/t"]!!
                .readOperationsMap()!![PathItem.HttpMethod.GET]!!
                .responses["200"]!!.content!!["application/json"]!!.schema
                .`$ref` shouldBe "#/components/schemas/RenamedWidget"
        }

        "sequential openapiDsl builds do not leak schemas from one spec to the next" {
            val api1 = apiWithMediaTypeRef<RegistryRoot>()
            val api2 = apiWithMediaTypeRef<IsolatedNode>()
            api1.components?.schemas?.get("RegistryRoot") shouldNotBe null
            api2.components?.schemas?.get("IsolatedNode") shouldNotBe null
            api2.components?.schemas?.get("RegistryRoot") shouldBe null
        }

        "exception during build cleans up the scope so no types leak into the next build" {
            val result = runCatching {
                openapiDsl {
                    info { title = "failing"; version = "1.0" }
                    paths {
                        path("/fail") {
                            get {
                                responses {
                                    response("200") {
                                        content { mediaTypeRef<RegistryRoot>("application/json") }
                                    }
                                }
                            }
                        }
                    }
                    throw RuntimeException("deliberate failure")
                }
            }
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "deliberate failure"
            openapiDsl { info { title = "clean"; version = "1.0" } }
                .components?.schemas?.get("RegistryRoot") shouldBe null
        }

        "parallel builds with distinct root types remain isolated per thread" {
            val pool = Executors.newFixedThreadPool(2)
            // latch releases both threads simultaneously to maximise scope overlap
            val latch = CountDownLatch(1)
            try {
                val futRoot = pool.submit(Callable<OpenAPI> { latch.await(); apiWithMediaTypeRef<RegistryRoot>() })
                val futNode = pool.submit(Callable<OpenAPI> { latch.await(); apiWithMediaTypeRef<IsolatedNode>() })
                latch.countDown()
                val apiRoot = futRoot.get()
                val apiNode = futNode.get()
                apiRoot.components?.schemas?.get("RegistryRoot") shouldNotBe null
                apiRoot.components?.schemas?.get("RegistryBranch") shouldNotBe null
                apiRoot.components?.schemas?.get("RegistryLeaf") shouldNotBe null
                apiNode.components?.schemas?.get("IsolatedNode") shouldNotBe null
                apiNode.components?.schemas?.get("RegistryRoot") shouldBe null
            } finally {
                pool.shutdownNow()
            }
        }

        "nested openapiDsl builds isolate each spec to its own type set" {
            var innerApi: OpenAPI? = null
            val outerApi = openapiDsl {
                info { title = "outer"; version = "1.0" }
                paths {
                    path("/outer") {
                        get {
                            responses {
                                response("200") {
                                    content { mediaTypeRef<RegistryRoot>("application/json") }
                                }
                            }
                        }
                    }
                }
                // inner build runs on the same thread while the outer scope is live on the stack
                innerApi = openapiDsl {
                    info { title = "inner"; version = "1.0" }
                    paths {
                        path("/inner") {
                            get {
                                responses {
                                    response("200") {
                                        content { mediaTypeRef<IsolatedNode>("application/json") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            outerApi.components?.schemas?.get("RegistryRoot") shouldNotBe null
            outerApi.components?.schemas?.get("IsolatedNode") shouldBe null
            val inner = innerApi!!
            inner.components?.schemas?.get("IsolatedNode") shouldNotBe null
            inner.components?.schemas?.get("RegistryRoot") shouldBe null
        }
    }
}
