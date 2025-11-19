import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.io.File

fun main() {
    val mapper = ObjectMapper()
    val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    
    // Load schema
    val schemaFile = File("src/main/resources/schemas/alerts/VOLUME_SPIKE.json")
    val schema = factory.getSchema(schemaFile.inputStream())
    
    // Test JSON
    val testJson = """
    {
      "operator": "ABOVE_MA",
      "lookback": 20,
      "multiplier": 3.0
    }
    """.trimIndent()
    
    val node = mapper.readTree(testJson)
    val errors = schema.validate(node)
    
    if (errors.isEmpty()) {
        println("✓ Schema validation PASSED")
    } else {
        println("✗ Schema validation FAILED:")
        errors.forEach { println("  - ${it.message}") }
    }
}
