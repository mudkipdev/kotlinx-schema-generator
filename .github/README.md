# kotlinx-schema-generator
Generate JSON Schema files from your Kotlin classes at runtime using `kotlinx.serialization`.

## Installation
<details>
<summary>Gradle (Kotlin)</summary>
<br>

```kts
dependencies {
    implementation("dev.mudkip:kotlinx-schema-generator:0.1.0")
}
```

</details>

<details>
<summary>Gradle (Groovy)</summary>
<br>

```groovy
dependencies {
    implementation 'dev.mudkip:kotlinx-schema-generator:0.1.0'
}
```

</details>

<details>
<summary>Maven</summary>
<br>

```xml
<dependency>
    <groupId>dev.mudkip</groupId>
    <artifactId>kotlinx-schema-generator</artifactId>
    <version>0.1.0</version>
</dependency>
```

</details>

## Examples
```kotlin
@Serializable
data class User(
    val id: String,
    @StringConstraints(format = StringConstraints.StringFormat.EMAIL)
    val email: String,
    val age: Int?
)

println(Schema().encodeToSchema<User>())
```

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "properties": {
        "id": {
            "type": "string"
        },
        "email": {
            "type": "string",
            "format": "email"
        },
        "age": {
            "type": [
                "integer",
                "null"
            ]
        }
    },
    "required": [
        "id",
        "email"
    ]
}
```

### Sealed class unions
```kotlin
@Serializable
sealed class Result { abstract val type: String }

@Serializable
@SerialName("ok")
data class Ok(override val type: String = "ok", val data: String) : Result()

@Serializable
@SerialName("err")
data class Err(override val type: String = "err", val message: String) : Result()

println(Schema().encodeToSchema<Result>())
```

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "oneOf": [
        {
            "$ref": "#/$defs/Ok"
        },
        {
            "$ref": "#/$defs/Err"
        }
    ],
    "discriminator": {
        "propertyName": "type",
        "mapping": {
            "ok": "#/$defs/Ok",
            "err": "#/$defs/Err"
        }
    },
    "$defs": {
        "Ok": {
            "type": "object",
            "properties": {
                "type": {
                    "type": "string"
                },
                "data": {
                    "type": "string"
                }
            },
            "required": [
                "data"
            ]
        },
        "Err": {
            "type": "object",
            "properties": {
                "type": {
                    "type": "string"
                },
                "message": {
                    "type": "string"
                }
            },
            "required": [
                "message"
            ]
        }
    }
}
```
