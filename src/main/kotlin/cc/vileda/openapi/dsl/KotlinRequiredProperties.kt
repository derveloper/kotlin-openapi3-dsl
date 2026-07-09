package cc.vileda.openapi.dsl

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

internal fun inferRequiredProperties(
    rootType: Class<*>,
    rootName: String,
    schemas: Map<String, Schema<*>>
) {
    RequiredPropertyInference(schemas).visit(rootType.kotlin, rootName)
}

private class RequiredPropertyInference(
    private val schemas: Map<String, Schema<*>>
) {
    private val visited = mutableSetOf<KClass<*>>()

    fun visit(type: KClass<*>, schemaName: String? = null) {
        if (!visited.add(type) || type.java.getAnnotation(Metadata::class.java) == null) return

        val schema = schemas[schemaName ?: componentName(type)] ?: return
        val properties = type.memberProperties.associateBy { it.name }
        val constructor = type.primaryConstructor ?: return

        constructor.parameters
            .filter { it.kind == KParameter.Kind.VALUE }
            .forEach { parameter ->
                val property = parameter.name?.let(properties::get)
                val annotations = schemaAnnotations(parameter, property)
                val propertyName = schemaPropertyName(parameter, property, schema, annotations)
                if (propertyName != null && shouldBeRequired(parameter, annotations)) {
                    if (schema.required?.contains(propertyName) != true) {
                        schema.addRequiredItem(propertyName)
                    }
                }
                visitNestedTypes(parameter.type)
            }
    }

    private fun visitNestedTypes(type: KType) {
        val classifier = type.classifier as? KClass<*> ?: return
        val javaType = classifier.java
        if (!javaType.isArray &&
            !Iterable::class.java.isAssignableFrom(javaType) &&
            !Map::class.java.isAssignableFrom(javaType)
        ) {
            visit(classifier)
        }
        type.arguments.forEach { argument -> argument.type?.let(::visitNestedTypes) }
    }

    private fun schemaPropertyName(
        parameter: KParameter,
        property: KProperty1<out Any, *>?,
        schema: Schema<*>,
        annotations: List<SchemaAnnotation>
    ): String? {
        val declaredName = parameter.name ?: return null
        val candidates = buildList {
            jsonPropertyNames(parameter, property).forEach(::add)
            annotations.mapNotNullTo(this) { it.name.takeIf(String::isNotBlank) }
            add(declaredName)
        }
        return candidates.firstOrNull { schema.properties?.containsKey(it) == true }
    }

    private fun shouldBeRequired(
        parameter: KParameter,
        annotations: List<SchemaAnnotation>
    ): Boolean {
        return when (annotations
            .map { it.requiredMode }
            .firstOrNull { it != SchemaAnnotation.RequiredMode.AUTO }) {
            SchemaAnnotation.RequiredMode.REQUIRED -> true
            SchemaAnnotation.RequiredMode.NOT_REQUIRED -> false
            else -> !parameter.type.isMarkedNullable && !parameter.isOptional
        }
    }

    private fun schemaAnnotations(
        parameter: KParameter,
        property: KProperty1<out Any, *>?
    ): List<SchemaAnnotation> {
        return listOfNotNull(
            parameter.findAnnotation(),
            property?.findAnnotation(),
            property?.javaField?.getAnnotation(SchemaAnnotation::class.java),
            property?.javaGetter?.getAnnotation(SchemaAnnotation::class.java)
        )
    }

    private fun jsonPropertyNames(
        parameter: KParameter,
        property: KProperty1<out Any, *>?
    ): List<String> {
        return listOfNotNull(
            parameter.findAnnotation<JsonProperty>()?.value,
            property?.findAnnotation<JsonProperty>()?.value,
            property?.javaField?.getAnnotation(JsonProperty::class.java)?.value,
            property?.javaGetter?.getAnnotation(JsonProperty::class.java)?.value
        ).filter(String::isNotBlank)
    }

    private fun componentName(type: KClass<*>): String {
        return type.java.getAnnotation(SchemaAnnotation::class.java)
            ?.name
            ?.takeIf(String::isNotBlank)
            ?: type.java.simpleName
    }
}
