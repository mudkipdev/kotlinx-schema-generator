package dev.mudkip.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

private const val schemaUri = "https://json-schema.org/draft/2020-12/schema"

@Suppress("unused")
@Serializable
private data class SimpleRecord(val name: String, val age: Int)

@Suppress("unused")
@Serializable
private data class StringConstraintRecord(
    @StringConstraints(minLength = 2, maxLength = 10, pattern = "[a-z]+", format = StringConstraints.StringFormat.EMAIL)
    val email: String
)

@Suppress("unused")
@Serializable
private data class NumberConstraintRecord(
    @NumberConstraints(minimum = 0.0, maximum = 10.5, exclusiveMinimum = -0.5, exclusiveMaximum = 20.0, multipleOf = 0.5)
    val score: Double,
    @IntConstraints(minimum = 1, maximum = 5, exclusiveMaximum = 6, multipleOf = 1)
    val level: Int
)

@Suppress("unused")
@Serializable
private data class CollectionRecord(
    @ArrayConstraints(minItems = 1, maxItems = 3, uniqueItems = true)
    val tags: List<String>
)

@Suppress("unused")
@Serializable
private data class MapRecord(
    val attributes: Map<String, Long>,
    val lookup: Map<Int, String>
)

@Suppress("unused")
@Serializable
private enum class Status {
    @SerialName("ok") OK,
    @SerialName("err") ERROR
}

@Suppress("unused")
@Serializable
private data class EnumHolder(val status: Status)

@Suppress("unused")
@Serializable
private data class NullableHolder(val score: Double?, val title: String?)

@Suppress("unused")
@Serializable
private sealed class Operation { abstract val type: String }

@Suppress("unused")
@Serializable
@SerialName("create")
private data class CreateOperation(
    override val type: String = "create",
    val payload: String
) : Operation()

@Suppress("unused")
@Serializable
@SerialName("delete")
private data class DeleteOperation(
    override val type: String = "delete",
    val id: Int
) : Operation()

@Suppress("unused")
@Serializable
@Flattened
private sealed class FlattenedShape { abstract val type: String }

@Suppress("unused")
@Serializable
@SerialName("circle")
private data class Circle(
    override val type: String = "circle",
    val radius: Double
) : FlattenedShape()

@Suppress("unused")
@Serializable
@SerialName("square")
private data class Square(
    override val type: String = "square",
    val side: Double
) : FlattenedShape()

class SchemaTest {
    @Test
    fun simpleObjectSchemaMatchesExpected() {
        val schema = schemaJson<SimpleRecord>("simple_object")
        assertEquals(expectedSimpleRecord(), schema)
    }

    @Test
    fun stringConstraintsAreIncluded() {
        val schema = schemaJson<StringConstraintRecord>("string_constraints")
        assertEquals(expectedStringConstraintProperty(), schema.requireProperties().requireObject("email"))
    }

    @Test
    fun numberConstraintsAreIncluded() {
        val properties = schemaJson<NumberConstraintRecord>("number_constraints").requireProperties()
        assertEquals(expectedNumberConstraintScore(), properties.requireObject("score"))
        assertEquals(expectedNumberConstraintLevel(), properties.requireObject("level"))
    }

    @Test
    fun arrayConstraintsAreIncluded() {
        val tags = schemaJson<CollectionRecord>("array_constraints").requireProperties().requireObject("tags")
        assertEquals(expectedCollectionTags(), tags)
    }

    @Test
    fun mapSchemasHandleAdditionalProperties() {
        val properties = schemaJson<MapRecord>("map_schemas").requireProperties()
        assertEquals(expectedMapAttributes(), properties.requireObject("attributes"))
        assertEquals(JsonPrimitive(true), properties.requireObject("lookup")["additionalProperties"])
    }

    @Test
    fun enumSerialNamesAppearInSchema() {
        val enumValues = schemaJson<EnumHolder>("enum_serial_names")
            .requireProperties()
            .requireObject("status")
            .requireArray("enum")
            .map { it.jsonPrimitive.content }

        assertEquals(setOf("ok", "err"), enumValues.toSet())
    }

    @Test
    fun nullableTypeArrayModeProducesTypeArray() {
        val properties = schemaJson<NullableHolder>("nullable_type_array").requireProperties()
        assertEquals(setOf("number", "null"), properties.requireObject("score").requireArray("type").stringSet())
        assertEquals(setOf("string", "null"), properties.requireObject("title").requireArray("type").stringSet())
    }

    @Test
    fun nullableOneOfModeProducesOneOf() {
        val schemaConfig = SchemaConfig(nullableMode = NullableMode.ONE_OF)
        val properties = schemaJson<NullableHolder>("nullable_one_of", Schema(schemaConfig)).requireProperties()
        val scoreTypes = properties.requireObject("score").requireArray("oneOf").map { it.jsonObject.requireStringType() }.toSet()
        assertEquals(setOf("number", "null"), scoreTypes)
    }

    @Test
    fun sealedUnionProducesReferencesAndMapping() {
        val schema = schemaJson<Operation>("sealed_union")
        val references = schema.requireArray("oneOf").map { it.jsonObject["\$ref"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("#/\$defs/String", "#/\$defs/Sealed<Operation>"), references)

        val mapping = schema.requireObject("discriminator").requireObject("mapping")
        val mappedDefs = mapping.values.map { it.jsonPrimitive.content.substringAfterLast('/') }.toSet()
        assertEquals(schema.requireObject("\$defs").keys, mappedDefs)
    }

    @Test
    fun flattenedUnionProducesAnyOfVariants() {
        val schema = schemaJson<FlattenedShape>("flattened_union")
        val anyOf = schema.requireArray("anyOf")
        assertEquals(2, anyOf.size)
        val constValues = anyOf.map { entry ->
            entry.jsonObject.requireProperties().requireObject("type")["const"]?.jsonPrimitive?.content
        }.toSet()
        assertEquals(setOf("String", "Sealed<FlattenedShape>"), constValues)
    }

    private inline fun <reified T> schemaJson(title: String, schema: Schema = Schema()): JsonObject {
        val schemaString = schema.encodeToSchema<T>()
        println("$title: $schemaString\n")
        return Json.parseToJsonElement(schemaString).jsonObject
    }

    private fun JsonObject.requireObject(key: String): JsonObject =
        this[key]?.jsonObject ?: error("Expected object at '$key' but found ${this[key]}")

    private fun JsonObject.requireArray(key: String): JsonArray =
        this[key]?.jsonArray ?: error("Expected array at '$key' but found ${this[key]}")

    private fun JsonObject.requireProperties(): JsonObject = requireObject("properties")

    private fun JsonArray.stringSet(): Set<String> = this.map { it.jsonPrimitive.content }.toSet()

    private fun JsonObject.requireStringType(): String = this["type"]?.jsonPrimitive?.content
        ?: error("Expected 'type' string but found $this")

    private fun expectedSimpleRecord(): JsonObject = buildJsonObject {
        put("\$schema", JsonPrimitive(schemaUri))
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            put("name", buildJsonObject { put("type", JsonPrimitive("string")) })
            put("age", buildJsonObject { put("type", JsonPrimitive("integer")) })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("name"))
            add(JsonPrimitive("age"))
        })
    }

    private fun expectedStringConstraintProperty(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("minLength", JsonPrimitive(2))
        put("maxLength", JsonPrimitive(10))
        put("pattern", JsonPrimitive("[a-z]+"))
        put("format", JsonPrimitive("email"))
    }

    private fun expectedNumberConstraintScore(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("number"))
        put("minimum", JsonPrimitive(0.0))
        put("maximum", JsonPrimitive(10.5))
        put("exclusiveMinimum", JsonPrimitive(-0.5))
        put("exclusiveMaximum", JsonPrimitive(20.0))
        put("multipleOf", JsonPrimitive(0.5))
    }

    private fun expectedNumberConstraintLevel(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("integer"))
        put("minimum", JsonPrimitive(1))
        put("maximum", JsonPrimitive(5))
        put("exclusiveMaximum", JsonPrimitive(6))
        put("multipleOf", JsonPrimitive(1))
    }

    private fun expectedCollectionTags(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("array"))
        put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
        put("minItems", JsonPrimitive(1))
        put("maxItems", JsonPrimitive(3))
        put("uniqueItems", JsonPrimitive(true))
    }

    private fun expectedMapAttributes(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("additionalProperties", buildJsonObject { put("type", JsonPrimitive("integer")) })
    }

}
