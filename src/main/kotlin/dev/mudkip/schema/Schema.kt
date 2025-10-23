@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.mudkip.schema

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

data class SchemaConfig(
    val prettyPrint: Boolean = true,
    val schemaUri: String = "https://json-schema.org/draft/2020-12/schema",
    val includeDiscriminatorMapping: Boolean = true,
    val nullableMode: NullableMode = NullableMode.TYPE_ARRAY
)

enum class NullableMode {
    TYPE_ARRAY,
    ONE_OF
}

class Schema(private val config: SchemaConfig = SchemaConfig()) {
    inline fun <reified T> encodeToSchema(): String = encodeToSchema(serializer<T>())

    fun <T> encodeToSchema(serializer: KSerializer<T>): String {
        val definitions = linkedMapOf<String, JsonObject>()
        val inProgress = mutableSetOf<String>()
        val root = generateSchema(serializer.descriptor, emptyList(), definitions, inProgress)
        val rootObject = root

        val final = buildJsonObject {
            put("\$schema", config.schemaUri)
            for ((key, value) in rootObject) put(key, value)
            if (definitions.isNotEmpty()) {
                put("\$defs", buildJsonObject { for ((k, v) in definitions) put(k, v) })
            }
        }

        val json = Json { prettyPrint = config.prettyPrint }
        return json.encodeToString(JsonObject.serializer(), final)
    }

    private fun generateSchema(
        descriptor: SerialDescriptor,
        annotations: List<Annotation>,
        definitions: MutableMap<String, JsonObject>,
        inProgress: MutableSet<String>
    ): JsonObject {
        return when (descriptor.kind) {
            PrimitiveKind.STRING -> stringSchema(annotations)

            PrimitiveKind.CHAR -> buildJsonObject {
                put("type", "string")
                put("minLength", 1)
                put("maxLength", 1)
            }

            PrimitiveKind.BOOLEAN -> buildJsonObject {
                put("type", "boolean")
            }

            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> integerSchema(annotations)
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> numberSchema(annotations)
            StructureKind.LIST -> arraySchema(descriptor, annotations, definitions, inProgress)
            StructureKind.MAP -> mapSchema(descriptor, annotations, definitions, inProgress)
            StructureKind.CLASS -> classSchema(descriptor, definitions, inProgress)

            StructureKind.OBJECT -> buildJsonObject {
                put("type", "object")
            }

            is SerialKind.ENUM -> enumSchema(descriptor)

            is PolymorphicKind -> when (descriptor.kind) {
                PolymorphicKind.SEALED -> sealedUnionSchema(descriptor, definitions, inProgress)
                PolymorphicKind.OPEN -> buildJsonObject { put("anyOf", JsonArray(emptyList())) }
                else -> unsupported(descriptor)
            }

            else -> unsupported(descriptor)
        }.let { maybeNullable(descriptor, it) }
    }

    private fun stringSchema(annotations: List<Annotation>): JsonObject = buildJsonObject {
        put("type", "string")
        annotations.filterIsInstance<SchemaDescription>().firstOrNull()?.let { put("description", it.value) }
        annotations.filterIsInstance<StringConstraints>().firstOrNull()?.let { constraints ->
            if (constraints.minLength >= 0) put("minLength", constraints.minLength)
            if (constraints.maxLength >= 0) put("maxLength", constraints.maxLength)
            if (constraints.pattern.isNotEmpty()) put("pattern", constraints.pattern)
            constraints.format.jsonFormat?.let { put("format", it) }
        }
    }

    private fun integerSchema(annotations: List<Annotation>): JsonObject = buildJsonObject {
        put("type", "integer")
        annotations.filterIsInstance<SchemaDescription>().firstOrNull()?.let { put("description", it.value) }
        annotations.filterIsInstance<IntConstraints>().firstOrNull()?.let { constraints ->
            if (constraints.minimum != Long.MIN_VALUE) put("minimum", constraints.minimum)
            if (constraints.maximum != Long.MAX_VALUE) put("maximum", constraints.maximum)
            if (constraints.exclusiveMinimum != Long.MIN_VALUE) put("exclusiveMinimum", constraints.exclusiveMinimum)
            if (constraints.exclusiveMaximum != Long.MAX_VALUE) put("exclusiveMaximum", constraints.exclusiveMaximum)
            if (constraints.multipleOf > 0) put("multipleOf", constraints.multipleOf)
        }
    }

    private fun numberSchema(annotations: List<Annotation>): JsonObject = buildJsonObject {
        put("type", "number")
        annotations.filterIsInstance<SchemaDescription>().firstOrNull()?.let { put("description", it.value) }
        annotations.filterIsInstance<NumberConstraints>().firstOrNull()?.let { constraints ->
            if (constraints.minimum != Double.NEGATIVE_INFINITY) put("minimum", constraints.minimum)
            if (constraints.maximum != Double.POSITIVE_INFINITY) put("maximum", constraints.maximum)
            if (constraints.exclusiveMinimum != Double.NEGATIVE_INFINITY) put("exclusiveMinimum", constraints.exclusiveMinimum)
            if (constraints.exclusiveMaximum != Double.POSITIVE_INFINITY) put("exclusiveMaximum", constraints.exclusiveMaximum)
            if (!constraints.multipleOf.isNaN()) put("multipleOf", constraints.multipleOf)
        }
    }

    private fun arraySchema(
        descriptor: SerialDescriptor,
        annotations: List<Annotation>,
        definitions: MutableMap<String, JsonObject>,
        inProgress: MutableSet<String>
    ): JsonObject = buildJsonObject {
        put("type", "array")
        val elementDescriptor = descriptor.getElementDescriptor(0)
        put("items", generateSchema(elementDescriptor, emptyList(), definitions, inProgress))

        annotations.filterIsInstance<ArrayConstraints>().firstOrNull()?.let { constraints ->
            if (constraints.minItems >= 0) put("minItems", constraints.minItems)
            if (constraints.maxItems >= 0) put("maxItems", constraints.maxItems)
            if (constraints.uniqueItems) put("uniqueItems", true)
        }
    }

    private fun mapSchema(
        descriptor: SerialDescriptor,
        annotations: List<Annotation>,
        definitions: MutableMap<String, JsonObject>,
        inProgress: MutableSet<String>
    ): JsonObject = buildJsonObject {
        put("type", "object")
        val keyDescriptor = descriptor.getElementDescriptor(0)
        val valueDescriptor = descriptor.getElementDescriptor(1)

        if (keyDescriptor.kind != PrimitiveKind.STRING) {
            put("additionalProperties", true)
        } else {
            put("additionalProperties", generateSchema(valueDescriptor, emptyList(), definitions, inProgress))
        }

        annotations.filterIsInstance<ObjectConstraints>().firstOrNull()?.let { constraints ->
            if (constraints.minProperties >= 0) put("minProperties", constraints.minProperties)
            if (constraints.maxProperties >= 0) put("maxProperties", constraints.maxProperties)
        }
    }

    private fun classSchema(
        descriptor: SerialDescriptor,
        definitions: MutableMap<String, JsonObject>,
        inProgress: MutableSet<String>
    ): JsonObject = buildJsonObject {
        put("type", "object")
        descriptor.annotations.filterIsInstance<SchemaDescription>().firstOrNull()?.let { put("description", it.value) }
        descriptor.annotations.filterIsInstance<ObjectConstraints>().firstOrNull()?.let { constraints ->
            if (constraints.minProperties >= 0) put("minProperties", constraints.minProperties)
            if (constraints.maxProperties >= 0) put("maxProperties", constraints.maxProperties)
        }

        val properties = buildJsonObject {
            for (i in 0 until descriptor.elementsCount) {
                val name = descriptor.getElementName(i)
                val propertyDescriptor = descriptor.getElementDescriptor(i)
                val propertyAnnotations = descriptor.getElementAnnotations(i)
                if (propertyAnnotations.any { it is kotlinx.serialization.Transient }) continue
                put(name, generateSchema(propertyDescriptor, propertyAnnotations, definitions, inProgress))
            }
        }

        if (properties.isNotEmpty()) put("properties", properties)
        val requiredProperties = buildRequired(descriptor)
        if (requiredProperties.isNotEmpty()) put("required", JsonArray(requiredProperties.map { JsonPrimitive(it) }))
    }

    private fun buildRequired(descriptor: SerialDescriptor): List<String> {
        val list = mutableListOf<String>()

        for (index in 0 until descriptor.elementsCount) {
            if (!descriptor.isElementOptional(index)) list += descriptor.getElementName(index)
        }

        return list
    }

    private fun enumSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {
        put("type", "string")
        val values = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        put("enum", JsonArray(values.map { JsonPrimitive(it) }))
    }

    private fun sealedUnionSchema(
        descriptor: SerialDescriptor,
        definitions: MutableMap<String, JsonObject>,
        inProgress: MutableSet<String>
    ): JsonObject {
        val variantDescriptors = (0 until descriptor.elementsCount).map { descriptor.getElementDescriptor(it) }
        val mapping = linkedMapOf<String, String>()
        val references = mutableListOf<JsonObject>()

        for (sub in variantDescriptors) {
            val preferredName = sub.serialName.substringAfterLast('.')
            val schemaForSubclass = classSchema(sub, definitions, inProgress)
            val uniqueName = makeUniqueName(definitions, preferredName)
            definitions[uniqueName] = schemaForSubclass
            val mappingKey = sub.annotations.filterIsInstance<SerialName>().firstOrNull()?.value ?: preferredName
            mapping[mappingKey] = "#/\$defs/$uniqueName"
            references += buildJsonObject { put("\$ref", "#/\$defs/$uniqueName") }
        }

        val discriminatorProperty = guessDiscriminatorProperty(variantDescriptors) ?: "type"

        if (descriptor.annotations.any { it is Flattened }) {
            val flattened = variantDescriptors.map { sub ->
                val subtypeSchema = classSchema(sub, definitions, inProgress)
                val constantValue = sub.annotations.filterIsInstance<SerialName>().firstOrNull()?.value
                    ?: sub.serialName.substringAfterLast('.')

                // remove possible discriminator property if present
                val originalProperties = subtypeSchema["properties"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                originalProperties.remove(discriminatorProperty)

                val merged = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put(discriminatorProperty, buildJsonObject {
                            put("const", constantValue)
                        })

                        for ((key, value) in originalProperties) put(key, value)
                    })

                    val required = mutableListOf<String>(discriminatorProperty)

                    subtypeSchema["required"]?.jsonArray?.let { arr ->
                        required.addAll(arr.mapNotNull { it.jsonPrimitive.content }.filter { it != discriminatorProperty })
                    }

                    put("required", JsonArray(required.map { JsonPrimitive(it) }))
                }
                merged
            }

            return buildJsonObject { put("anyOf", JsonArray(flattened)) }
        }

        return buildJsonObject {
            put("oneOf", JsonArray(references))

            if (config.includeDiscriminatorMapping) {
                put("discriminator", buildJsonObject {
                    put("propertyName", discriminatorProperty)
                    put("mapping", buildJsonObject {
                        for ((key, value) in mapping) put(key, value)
                    })
                })
            } else {
                put("discriminator", buildJsonObject {
                    put("propertyName", discriminatorProperty)
                })
            }
        }
    }

    private fun makeUniqueName(definitions: Map<String, JsonObject>, base: String): String {
        if (!definitions.containsKey(base)) {
            return base
        }

        var counter = 2
        while (definitions.containsKey("${'$'}base_${'$'}counter")) counter++
        return "${'$'}base_${'$'}counter"
    }

    private fun guessDiscriminatorProperty(subclasses: List<SerialDescriptor>): String? {
        val candidate = "type"
        val allHave = subclasses.all { sub ->
            val index = (0 until sub.elementsCount).firstOrNull { sub.getElementName(it) == candidate }
            if (index == null) false else sub.getElementDescriptor(index).kind == PrimitiveKind.STRING
        }

        return if (allHave) candidate else null
    }

    private fun maybeNullable(descriptor: SerialDescriptor, base: JsonObject): JsonObject {
        if (!descriptor.isNullable) {
            return base
        }

        return when (config.nullableMode) {
            NullableMode.TYPE_ARRAY -> {
                val baseType = base["type"]?.jsonPrimitive?.content

                if (baseType != null && base["\$ref"] == null && base["oneOf"] == null && base["anyOf"] == null) {
                    buildJsonObject {
                        for ((k, v) in base) {
                            if (k != "type") {
                                put(k, v)
                            }
                        }

                        put("type", JsonArray(listOf(JsonPrimitive(baseType), JsonPrimitive("null"))))
                    }
                } else {
                    buildJsonObject {
                        put("oneOf", JsonArray(listOf(base, buildJsonObject { put("type", "null") })))
                    }
                }
            }

            NullableMode.ONE_OF -> buildJsonObject {
                put("oneOf", JsonArray(listOf(base, buildJsonObject { put("type", "null") })))
            }
        }
    }

    private fun unsupported(descriptor: SerialDescriptor): JsonObject {
        throw RuntimeException("Type ${descriptor.serialName} with kind ${descriptor.kind} is not supported")
    }
}
