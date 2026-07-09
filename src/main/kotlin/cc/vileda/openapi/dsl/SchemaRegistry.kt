package cc.vileda.openapi.dsl

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.BooleanSchema
import io.swagger.v3.oas.models.media.DateSchema
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.ArrayDeque
import java.util.Date
import java.util.LinkedHashMap

@PublishedApi
internal data class ResolvedTypeSchemas(
    val rootName: String,
    val root: Schema<*>,
    val schemas: Map<String, Schema<*>>
)

private class SchemaBuildScope {
    private val resolvedTypes = LinkedHashMap<Class<*>, ResolvedTypeSchemas>()

    fun register(type: Class<*>): String {
        resolvedTypes[type]?.let { return it.rootName }
        val resolved = resolveTypeSchemas(type) ?: return type.simpleName
        resolvedTypes[type] = resolved
        return resolved.rootName
    }

    fun commitTo(api: OpenAPI) {
        if (resolvedTypes.isEmpty()) return

        val components = api.components ?: Components().also { api.components = it }
        resolvedTypes.values.forEach { resolved ->
            resolved.schemas.forEach { (name, schema) ->
                if (components.schemas?.containsKey(name) != true) {
                    components.addSchemas(name, schema)
                }
            }
        }
    }
}

private val schemaScopes = ThreadLocal<ArrayDeque<SchemaBuildScope>>()

internal fun buildOpenApi(init: OpenAPI.() -> Unit): OpenAPI {
    val stack = schemaScopes.get() ?: ArrayDeque<SchemaBuildScope>().also(schemaScopes::set)
    val scope = SchemaBuildScope()
    stack.addLast(scope)

    return try {
        OpenAPI().also { api ->
            api.init()
            scope.commitTo(api)
        }
    } finally {
        stack.removeLast()
        if (stack.isEmpty()) schemaScopes.remove()
    }
}

@PublishedApi
internal fun registerSchemaReference(type: Class<*>): String {
    return schemaScopes.get()?.peekLast()?.register(type) ?: type.simpleName
}

@PublishedApi
internal fun resolveTypeSchemas(type: Class<*>): ResolvedTypeSchemas? {
    val knownSchema = knownSchema(type)
    if (knownSchema != null) {
        return ResolvedTypeSchemas(
            rootName = type.simpleName,
            root = knownSchema,
            schemas = linkedMapOf(type.simpleName to knownSchema)
        )
    }

    val resolved = ModelConverters.getInstance().readAllAsResolvedSchema(type) ?: return null
    val root = resolved.schema ?: return null
    val schemas = LinkedHashMap<String, Schema<*>>()
    resolved.referencedSchemas?.forEach { (name, schema) -> schemas[name] = schema }
    val rootName = schemas.entries.firstOrNull { (_, schema) ->
        schema === root || schema == root
    }?.key ?: type.simpleName
    schemas.putIfAbsent(rootName, root)

    return ResolvedTypeSchemas(rootName, root, schemas)
}

@PublishedApi
internal fun Components.addResolvedSchemas(resolved: ResolvedTypeSchemas) {
    resolved.schemas.forEach { (name, schema) ->
        if (schemas?.containsKey(name) != true) addSchemas(name, schema)
    }
}

@PublishedApi
internal fun enumSchema(type: Class<*>): Schema<*>? {
    val values = type.enumConstants ?: return null
    return StringSchema().apply {
        values.forEach { addEnumItem(it.toString()) }
    }
}

private fun knownSchema(type: Class<*>): Schema<*>? {
    return enumSchema(type) ?: when (type) {
        String::class.java -> StringSchema()
        Boolean::class.java, Boolean::class.javaObjectType -> BooleanSchema()
        Int::class.java, Int::class.javaObjectType -> IntegerSchema()
        List::class.java -> ArraySchema()
        Long::class.java, Long::class.javaObjectType -> IntegerSchema().format("int64")
        BigDecimal::class.java -> IntegerSchema().format("")
        Date::class.java, LocalDate::class.java -> DateSchema()
        LocalDateTime::class.java -> DateTimeSchema()
        else -> null
    }
}
