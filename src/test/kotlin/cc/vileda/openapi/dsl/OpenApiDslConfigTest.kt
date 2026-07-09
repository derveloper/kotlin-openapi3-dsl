package cc.vileda.openapi.dsl

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import io.swagger.v3.oas.models.OpenAPI
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private data class ReqOffBase(val active: String, val inactive: String?)

private data class ReqMixBase(
    val name: String,
    val tag: String?,
    val label: String = "x"
)

private data class ReqChild(val childId: Int, val childTag: String?)
private data class ReqParent(val name: String, val child: ReqChild)

private data class ReqRenamed(
    @JsonProperty("display_name") val displayName: String,
    val count: Int
)

private data class ReqExplicit(
    @SchemaAnnotation(requiredMode = SchemaAnnotation.RequiredMode.REQUIRED)
    val nullableProp: String?,
    @SchemaAnnotation(requiredMode = SchemaAnnotation.RequiredMode.NOT_REQUIRED)
    val nonNullProp: String
)

private data class ReqWithIgnored(
    val active: String,
    @JsonIgnore val secret: String
)

private data class ReqListItem(val code: String, val label: String?)
private data class ReqListContainer(val items: List<ReqListItem>)

private data class ReqMapValue(val v: String, val extra: String?)
private data class ReqMapContainer(val entries: Map<String, ReqMapValue>)

private data class ReqNestedOuter(val outerProp: String)
private data class ReqNestedInner(val innerProp: String)

private data class ReqParallelOn(val mustBeRequired: String, val mayBeNull: String?)
private data class ReqParallelOff(val nonNull: String, val alsoNull: String?)

private data class ReqSequential(val nonNullProp: String, val nullableProp: String?)

private val inferOn = OpenApiDslConfig(inferRequiredFromKotlinNullability = true)

private inline fun <reified T : Any> schemasWithInference(): Map<String, io.swagger.v3.oas.models.media.Schema<*>>? =
    openapiDsl(inferOn) {
        info { title = "t"; version = "1.0" }
        components { schema<T>() }
    }.components?.schemas

class OpenApiDslConfigTest : StringSpec() {
    init {

        "default openapiDsl config does not add required properties from Kotlin nullability" {
            val api = openapiDsl {
                info { title = "t"; version = "1.0" }
                components { schema<ReqOffBase>() }
            }
            api.components?.schemas?.get("ReqOffBase")?.required shouldBe null
        }

        "opt-in inference marks only non-null non-defaulted constructor props as required" {
            val required = schemasWithInference<ReqMixBase>()!!["ReqMixBase"]?.required
            required shouldBe listOf("name")
        }

        "inference recurses into referenced child models and applies the same rules" {
            val schemas = schemasWithInference<ReqParent>()!!
            // OpenAPI required order is unspecified; compare as sets
            schemas["ReqParent"]?.required?.toSet() shouldBe setOf("name", "child")
            schemas["ReqChild"]?.required shouldBe listOf("childId")
        }

        "@JsonIgnore property is absent from schema properties and therefore absent from required" {
            val required = schemasWithInference<ReqWithIgnored>()!!["ReqWithIgnored"]?.required
            required shouldBe listOf("active")
        }

        "inference uses the @JsonProperty-renamed schema key, not the Kotlin declaration name" {
            val required = schemasWithInference<ReqRenamed>()!!["ReqRenamed"]?.required
            required shouldNotBe null
            // @JsonProperty propagates to the backing field; Jackson generates "display_name" in the schema
            required!!.contains("display_name") shouldBe true
            required.contains("displayName") shouldBe false
            required.contains("count") shouldBe true
        }

        "@Schema REQUIRED and NOT_REQUIRED override inference for their respective properties" {
            val required = schemasWithInference<ReqExplicit>()!!["ReqExplicit"]?.required
            required?.contains("nullableProp") shouldBe true   // nullable but forced required
            required?.contains("nonNullProp") shouldBe false   // non-null but forced not-required
        }

        "inference recurses into the element type of a List property" {
            val schemas = schemasWithInference<ReqListContainer>()!!
            schemas["ReqListContainer"]?.required shouldBe listOf("items")
            schemas["ReqListItem"]?.required shouldBe listOf("code")
        }

        "inference recurses into the value type of a Map property" {
            val schemas = schemasWithInference<ReqMapContainer>()!!
            schemas["ReqMapContainer"]?.required shouldBe listOf("entries")
            schemas["ReqMapValue"]?.required shouldBe listOf("v")
        }

        "nested DSL builds each honour their own config independently" {
            var innerApi: OpenAPI? = null
            val outerApi = openapiDsl(OpenApiDslConfig(inferRequiredFromKotlinNullability = false)) {
                info { title = "outer"; version = "1.0" }
                // Only outer scope (inference=false) is on the ThreadLocal stack here
                components { schema<ReqNestedOuter>() }
                // Inner build pushes its own scope; peekLast() returns it (inference=true)
                innerApi = openapiDsl(OpenApiDslConfig(inferRequiredFromKotlinNullability = true)) {
                    info { title = "inner"; version = "1.0" }
                    components { schema<ReqNestedInner>() }
                }
            }
            outerApi.components?.schemas?.get("ReqNestedOuter")?.required shouldBe null
            innerApi!!.components?.schemas?.get("ReqNestedInner")?.required shouldBe listOf("innerProp")
        }

        "parallel builds with opposite configs remain isolated per thread" {
            val pool = Executors.newFixedThreadPool(2)
            // latch releases both threads simultaneously to maximise ThreadLocal scope overlap
            val latch = CountDownLatch(1)
            try {
                val futOn = pool.submit(Callable<OpenAPI> {
                    latch.await()
                    openapiDsl(OpenApiDslConfig(inferRequiredFromKotlinNullability = true)) {
                        info { title = "on"; version = "1.0" }
                        components { schema<ReqParallelOn>() }
                    }
                })
                val futOff = pool.submit(Callable<OpenAPI> {
                    latch.await()
                    openapiDsl(OpenApiDslConfig(inferRequiredFromKotlinNullability = false)) {
                        info { title = "off"; version = "1.0" }
                        components { schema<ReqParallelOff>() }
                    }
                })
                latch.countDown()
                val apiOn = futOn.get()
                val apiOff = futOff.get()
                apiOn.components?.schemas?.get("ReqParallelOn")?.required
                    ?.contains("mustBeRequired") shouldBe true
                apiOff.components?.schemas?.get("ReqParallelOff")?.required shouldBe null
            } finally {
                pool.shutdownNow()
            }
        }

        "same model class resolved sequentially with opposite configs does not leak required from first build into second" {
            val apiOn = openapiDsl(OpenApiDslConfig(inferRequiredFromKotlinNullability = true)) {
                info { title = "seq-on"; version = "1.0" }
                components { schema<ReqSequential>() }
            }
            val apiOff = openapiDsl(OpenApiDslConfig(inferRequiredFromKotlinNullability = false)) {
                info { title = "seq-off"; version = "1.0" }
                components { schema<ReqSequential>() }
            }
            apiOn.components?.schemas?.get("ReqSequential")?.required?.contains("nonNullProp") shouldBe true
            apiOff.components?.schemas?.get("ReqSequential")?.required shouldBe null
        }
    }
}
