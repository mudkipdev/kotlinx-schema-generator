@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.mudkip.schema

import kotlinx.serialization.SerialInfo

@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class SchemaDescription(val value: String)

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class StringConstraints(
    val minLength: Int = -1,
    val maxLength: Int = -1,
    val pattern: String = "",
    val formats: Array<StringFormat> = []
) {
    enum class StringFormat(val jsonFormat: String?) {
        NONE(null),
        DATE_TIME("date-time"),
        DATE("date"),
        TIME("time"),
        DURATION("duration"),
        EMAIL("email"),
        IDN_EMAIL("idn-email"),
        HOSTNAME("hostname"),
        IDN_HOSTNAME("idn-hostname"),
        IPV4("ipv4"),
        IPV6("ipv6"),
        URI("uri"),
        URI_REFERENCE("uri-reference"),
        UUID("uuid"),
        REGEX("regex")
    }
}

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class NumberConstraints(
    val minimum: Double = Double.NEGATIVE_INFINITY,
    val maximum: Double = Double.POSITIVE_INFINITY,
    val exclusiveMinimum: Double = Double.NEGATIVE_INFINITY,
    val exclusiveMaximum: Double = Double.POSITIVE_INFINITY,
    val multipleOf: Double = Double.NaN
)

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class IntConstraints(
    val minimum: Long = Long.MIN_VALUE,
    val maximum: Long = Long.MAX_VALUE,
    val exclusiveMinimum: Long = Long.MIN_VALUE,
    val exclusiveMaximum: Long = Long.MAX_VALUE,
    val multipleOf: Long = -1
)

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class ArrayConstraints(
    val minItems: Int = -1,
    val maxItems: Int = -1,
    val uniqueItems: Boolean = false
)

@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class ObjectConstraints(
    val minProperties: Int = -1,
    val maxProperties: Int = -1
)

@SerialInfo
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Flattened
