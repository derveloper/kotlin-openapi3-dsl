package cc.vileda.openapi.dsl

/** Options applied to one [openapiDsl] build. */
data class OpenApiDslConfig(
    /**
     * Adds non-null Kotlin primary-constructor properties without default values
     * to the generated schema's `required` list.
     */
    val inferRequiredFromKotlinNullability: Boolean = false
)
