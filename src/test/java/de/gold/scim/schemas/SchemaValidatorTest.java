package de.gold.scim.schemas;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import de.gold.scim.constants.AttributeNames;
import de.gold.scim.constants.ClassPathReferences;
import de.gold.scim.constants.SchemaUris;
import de.gold.scim.constants.enums.Mutability;
import de.gold.scim.constants.enums.Returned;
import de.gold.scim.constants.enums.Type;
import de.gold.scim.constants.enums.Uniqueness;
import de.gold.scim.exceptions.DocumentValidationException;
import de.gold.scim.resources.base.ScimNode;
import de.gold.scim.utils.FileReferences;
import de.gold.scim.utils.JsonHelper;
import lombok.extern.slf4j.Slf4j;


/**
 * author Pascal Knueppel <br>
 * created at: 06.10.2019 - 18:09 <br>
 * <br>
 */
@Slf4j
public class SchemaValidatorTest implements FileReferences
{

  private ResourceTypeFactory resourceTypeFactory;

  /**
   * defines the schema - document pairs that should be validated
   */
  private static Stream<Arguments> getSchemaValidations()
  {
    return Stream.of(Arguments.of("check user schema definition",
                                  JsonHelper.loadJsonDocument(ClassPathReferences.META_SCHEMA_JSON),
                                  JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON)),
                     Arguments.of("check enterprise user schema definition",
                                  JsonHelper.loadJsonDocument(ClassPathReferences.META_SCHEMA_JSON),
                                  JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON)),
                     Arguments.of("check group schema definition",
                                  JsonHelper.loadJsonDocument(ClassPathReferences.META_SCHEMA_JSON),
                                  JsonHelper.loadJsonDocument(ClassPathReferences.GROUP_SCHEMA_JSON)),
                     Arguments.of("check user-resourceType schema definition",
                                  JsonHelper.loadJsonDocument(ClassPathReferences.META_RESOURCE_TYPES_JSON),
                                  JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON)),
                     Arguments.of("check group-resourceType schema definition",
                                  JsonHelper.loadJsonDocument(ClassPathReferences.META_RESOURCE_TYPES_JSON),
                                  JsonHelper.loadJsonDocument(ClassPathReferences.GROUP_RESOURCE_TYPE_JSON)),
                     Arguments.of("check enterprise-user validation",
                                  JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON),
                                  JsonHelper.loadJsonDocument(USER_RESOURCE_ENTERPRISE)));
  }

  /**
   * will produce a number of timestamp arguments for testing date parsing on scim documents
   */
  private static Stream<Arguments> getTimeStampArguments()
  {
    return Stream.of(Arguments.of(OffsetDateTime.now().withNano(0).format(DateTimeFormatter.ISO_DATE_TIME)),
                     Arguments.of(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()),
                     Arguments.of(Instant.now()
                                         .atOffset(ZoneOffset.ofHours(14))
                                         .withNano(0)
                                         .format(DateTimeFormatter.ISO_DATE_TIME)),
                     Arguments.of(Instant.now()
                                         .atOffset(ZoneOffset.ofHours(-14))
                                         .withHour(0)
                                         .withMinute(0)
                                         .withSecond(0)
                                         .withNano(0)
                                         .format(DateTimeFormatter.ISO_DATE_TIME)),
                     Arguments.of(Instant.now()
                                         .atOffset(ZoneOffset.ofHours(-10))
                                         .withNano(0)
                                         .format(DateTimeFormatter.ISO_DATE_TIME)),
                     Arguments.of(LocalDateTime.now().toString()),
                     Arguments.of(LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_DATE_TIME)),
                     Arguments.of(LocalDateTime.now()
                                               .withSecond(0)
                                               .withNano(0)
                                               .format(DateTimeFormatter.ISO_DATE_TIME)),
                     Arguments.of(LocalDateTime.now()
                                               .atOffset(ZoneOffset.ofHours(3))
                                               .withHour(0)
                                               .withMinute(0)
                                               .withSecond(0)
                                               .withNano(0)
                                               .format(DateTimeFormatter.ISO_DATE_TIME)),
                     Arguments.of(LocalDateTime.now()
                                               .withHour(0)
                                               .withMinute(0)
                                               .withSecond(0)
                                               .withNano(0)
                                               .format(DateTimeFormatter.ISO_DATE_TIME)),
                     Arguments.of("2019-09-29T24:00:00"),
                     Arguments.of("2019-09-29T24:00:00"),
                     Arguments.of("2019-09-29T24:00:00.0000000"),
                     Arguments.of("2019-09-29T24:00:00Z"),
                     Arguments.of("2019-09-29T24:00:00.0000000Z"),
                     Arguments.of("2019-09-29T24:00:00.0000000-10:00"),
                     Arguments.of("2019-09-29T24:00:00.0000000+10:00"),
                     Arguments.of("2019-09-29T24:00:00.0000000-14:00"),
                     Arguments.of("2019-09-29T24:00:00.0000000+14:00"));
  }

  /**
   * calls itself recursively to verify that all existing nodes are of type {@link ScimNode}
   *
   * @param validatedDocument the node to verify that it is a {@link ScimNode}
   */
  public static void validateJsonNodeIsScimNode(JsonNode validatedDocument)
  {
    Assertions.assertTrue(validatedDocument instanceof ScimNode);
    ScimNode scimNode = (ScimNode)validatedDocument;
    log.trace(scimNode.getScimNodeName());
    if (validatedDocument.isArray() || validatedDocument.isObject())
    {
      for ( JsonNode jsonNode : validatedDocument )
      {
        validateJsonNodeIsScimNode(jsonNode);
      }
    }
  }

  @BeforeEach
  public void initialize()
  {
    this.resourceTypeFactory = ResourceTypeFactory.getUnitTestInstance();
  }

  /**
   * validates the schemata from the classpath
   *
   * @param testName the name of the test
   * @param metaSchemaNode the meta schema that describes the given json document
   * @param jsonDocument the json document that is validated against the schema
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("getSchemaValidations")
  public void testSchemaValidationForUserResourceSchema(String testName, JsonNode metaSchemaNode, JsonNode jsonDocument)
  {
    log.trace(testName);
    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode jsonNode = SchemaValidator.validateDocumentForResponse(resourceTypeFactory, metaSchema, jsonDocument);
    Assertions.assertTrue(JsonHelper.getArrayAttribute(jsonNode, AttributeNames.SCHEMAS).isPresent(),
                          "the schemas attribute must not be removed from the document");
    ArrayNode documentSchemas = JsonHelper.getArrayAttribute(jsonDocument, AttributeNames.SCHEMAS).get();
    ArrayNode jsonNodeSchemas = JsonHelper.getArrayAttribute(jsonNode, AttributeNames.SCHEMAS).get();
    Assertions.assertEquals(documentSchemas, jsonNodeSchemas);
  }

  /**
   * checks that the validation will fail if a required attribute is missing
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.SCHEMAS, AttributeNames.NAME, AttributeNames.SCHEMA, AttributeNames.ENDPOINT})
  public void testValidationFailsOnMissingRequiredAttribute(String attributeName)
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.META_RESOURCE_TYPES_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);

    JsonHelper.removeAttribute(userSchema, attributeName);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                              metaSchema,
                                                                              userSchema));
  }

  /**
   * checks that the validation will fail if a required attribute is missing within a sub-attribute
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.NAME, AttributeNames.TYPE, AttributeNames.MULTI_VALUED})
  public void testValidationFailsOnMissingRequiredSubAttribute(String attributeName)
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.META_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);

    JsonNode attributes = JsonHelper.getArrayAttribute(userSchema, AttributeNames.ATTRIBUTES).get();
    JsonNode firstAttribute = attributes.get(0);
    JsonHelper.removeAttribute(firstAttribute, attributeName);

    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                              metaSchema,
                                                                              userSchema));
  }

  /**
   * checks that the validation will fail if a canonical value has a typo
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.MUTABILITY, AttributeNames.TYPE, AttributeNames.RETURNED,
                          AttributeNames.UNIQUENESS})
  public void testValidationFailsOnTypoInCanonicalValue(String attributeName)
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.META_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);

    JsonNode attributes = JsonHelper.getArrayAttribute(userSchema, AttributeNames.ATTRIBUTES).get();
    JsonNode firstAttribute = attributes.get(0);
    JsonHelper.writeValue(firstAttribute, attributeName, "unknown_value");
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                              metaSchema,
                                                                              userSchema));
  }

  /**
   * shows that the validation will fail if a field is an array but is expected by the schema as a simple value
   */
  @Test
  public void testValidationFailsIfNodeIsArrayInsteadOfSimple()
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.META_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);

    JsonNodeFactory factory = new JsonNodeFactory(false);
    ArrayNode arrayNode = new ArrayNode(factory);
    arrayNode.add("bla");
    JsonHelper.replaceNode(userSchema, AttributeNames.ID, arrayNode);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                              metaSchema,
                                                                              userSchema));
  }

  /**
   * shows that the validation will fail if a field is an array but is expected by the schema as a simple value
   */
  @Test
  public void testValidationFailsIfNodeIsOfDifferentType()
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.META_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);

    IntNode idNode = new IntNode(new Random().nextInt());
    JsonHelper.replaceNode(userSchema, AttributeNames.ID, idNode);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                              metaSchema,
                                                                              userSchema));
  }

  /**
   * this test will show that the validation will fail if a value marked as reference of type uri is not of type
   * uri
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.SCHEMA, AttributeNames.ENDPOINT})
  public void testValidationFailsIfUriReferenceIsNotAUri(String attributeName)
  {
    Schema resourceTypeSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.META_RESOURCE_TYPES_JSON));
    JsonNode userResourceTypeSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);

    JsonHelper.writeValue(userResourceTypeSchema, attributeName, "oh happy day");
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                              resourceTypeSchema,
                                                                              userResourceTypeSchema));
  }

  /**
   * this test will show that the validation of timestamps is successfully executed up to RFC7643 chapter 2.3.5
   */
  @ParameterizedTest
  @MethodSource("getTimeStampArguments")
  public void testValidationWithTimestampFields(String dateTime)
  {
    JsonNode resourceTypeSchema = JsonHelper.loadJsonDocument(ClassPathReferences.META_RESOURCE_TYPES_JSON);
    JsonNode userResourceTypeSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);

    addTimestampToMetaSchemaAndDocument(dateTime, resourceTypeSchema, userResourceTypeSchema);

    Schema metaSchema = new Schema(resourceTypeSchema);
    Assertions.assertDoesNotThrow(() -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                                    metaSchema,
                                                                                    userResourceTypeSchema));
  }

  /**
   * this test will verify that the validation fails if timestamps are used that do not fit the xsd:datetime
   * definition
   */
  @ParameterizedTest
  @ValueSource(strings = {"hello world", "123456", "2019-12-24", "2019-12-24 13:54:28"})
  public void testValidationFailsForInvalidTimestamps(String dateTime)
  {
    JsonNode resourceTypeSchema = JsonHelper.loadJsonDocument(ClassPathReferences.META_RESOURCE_TYPES_JSON);
    JsonNode userResourceTypeSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);

    addTimestampToMetaSchemaAndDocument(dateTime, resourceTypeSchema, userResourceTypeSchema);

    Schema metaSchema = new Schema(resourceTypeSchema);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                              metaSchema,
                                                                              userResourceTypeSchema));
  }

  /**
   * this method takes a dateTime value, a meta schema and a document and will add a timestamp definition to the
   * meta-schema, and the timestamp value to the document for validation
   *
   * @param dateTime the date time value that should be added to the document
   * @param metaSchema the meta schema that will need a datetime definition
   * @param document the document that must hold the datetime value
   */
  private void addTimestampToMetaSchemaAndDocument(String dateTime, JsonNode metaSchema, JsonNode document)
  {
    final String createdAttributeName = "created";
    String dateTimeTypeString = getAttributeString(createdAttributeName,
                                                   Type.DATE_TIME,
                                                   false,
                                                   true,
                                                   false,
                                                   Mutability.READ_WRITE,
                                                   Returned.DEFAULT,
                                                   Uniqueness.NONE);
    JsonNode metaAttributes = JsonHelper.getArrayAttribute(metaSchema, AttributeNames.ATTRIBUTES).get();
    JsonNode createMetaAttribute = JsonHelper.readJsonDocument(dateTimeTypeString);
    JsonHelper.addAttributeToArray(metaAttributes, createMetaAttribute);
    TextNode textNode = new TextNode(dateTime);
    JsonHelper.addAttribute(document, createdAttributeName, textNode);
  }

  /**
   * this test will show that the validation will also remove attributes that are not defined by the schema
   */
  @Test
  public void testRemoveUnknownAttributes()
  {
    Schema resourceTypeSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.META_RESOURCE_TYPES_JSON));
    JsonNode userResourceTypeSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);

    final String helloWorldKey = "helloWorld";
    JsonHelper.addAttribute(userResourceTypeSchema, helloWorldKey, new TextNode("hello world"));

    JsonNode validatedSchema = SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                           resourceTypeSchema,
                                                                           userResourceTypeSchema);
    Assertions.assertFalse(JsonHelper.getSimpleAttribute(validatedSchema, helloWorldKey).isPresent());
    ArrayNode schemaExtensions = JsonHelper.getArrayAttribute(validatedSchema, AttributeNames.SCHEMA_EXTENSIONS)
                                           .orElseThrow(() -> new IllegalStateException("the document does not contain "
                                                                                        + "an endpoint  attribute"));
    ObjectNode schemaExtensionAttribute = (ObjectNode)schemaExtensions.get(0);
    Assertions.assertFalse(JsonHelper.getSimpleAttribute(schemaExtensionAttribute, helloWorldKey).isPresent());

    Assertions.assertFalse(JsonHelper.getObjectAttribute(validatedSchema, AttributeNames.META).isPresent(),
                           "meta attribute must be removed from validated request-document");
  }

  /**
   * this test will verify that the validated nodes created by the {@link SchemaValidator} are all implementing
   * the interface {@link ScimNode}
   */
  @ParameterizedTest
  @CsvSource({ClassPathReferences.META_SCHEMA_JSON + "," + ClassPathReferences.USER_SCHEMA_JSON,
              ClassPathReferences.META_RESOURCE_TYPES_JSON + "," + ClassPathReferences.USER_RESOURCE_TYPE_JSON,
              ClassPathReferences.META_RESOURCE_TYPES_JSON + "," + ClassPathReferences.GROUP_RESOURCE_TYPE_JSON,
              ClassPathReferences.USER_SCHEMA_JSON + "," + USER_RESOURCE,
              ClassPathReferences.USER_SCHEMA_JSON + "," + USER_RESOURCE_ENTERPRISE,
              ClassPathReferences.GROUP_SCHEMA_JSON + "," + GROUP_RESOURCE})
  public void testThatAllValidatedNodesAreScimNodes(String metaSchemaLocation, String documentLocation)
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(metaSchemaLocation));
    JsonNode userSchema = JsonHelper.loadJsonDocument(documentLocation);

    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory, metaSchema, userSchema);
    });
    Assertions.assertNotNull(validatedDocument);
    validateJsonNodeIsScimNode(validatedDocument);
  }

  /**
   * this test will check that on response validation an exception is thrown if a required attribute is missing
   */
  @ParameterizedTest
  @CsvSource({ClassPathReferences.USER_SCHEMA_JSON + "," + USER_RESOURCE_ENTERPRISE,
              ClassPathReferences.GROUP_SCHEMA_JSON + "," + GROUP_RESOURCE})
  public void testValidationFailsForMissingIdOnResponse(String metaSchemaLocation, String documentLocation)
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(metaSchemaLocation));
    JsonNode resourceSchema = JsonHelper.loadJsonDocument(documentLocation);

    JsonHelper.removeAttribute(resourceSchema, AttributeNames.ID);
    Assertions.assertThrows(DocumentValidationException.class, () -> {
      SchemaValidator.validateDocumentForResponse(resourceTypeFactory, metaSchema, resourceSchema);
    });
  }

  /**
   * this test will verify that unknown attributes are removed from the validated document
   */
  @Test
  public void testRemoveEnterpriseExtensionFromValidatedDocument()
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE_ENTERPRISE);

    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                        metaSchema,
                                                        userSchema,
                                                        SchemaValidator.HttpMethod.POST);
    });
    // since the document was only validated against the user-schema and not the enterprise-user-extension schema
    // the extension attribute should not be present in the result
    Assertions.assertNull(validatedDocument.get(SchemaUris.ENTERPRISE_USER_URI));
  }

  /**
   * this test will verify never returned attributes are simply removed from responses
   */
  @Test
  public void testRemoveNeverReturnedAttributesFromResponse()
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory, metaSchema, userSchema);
    });
    // since the document was only validated against the user-schema and not the enterprise-user-extension schema
    // the extension attribute should not be present in the result
    Assertions.assertNull(validatedDocument.get(AttributeNames.PASSWORD));
  }

  /**
   * this test will verify read only attributes are simply removed from the request if they are not required
   */
  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT"})
  public void testRemoveNonRequiredReadOnlyAttributesFromRequest(SchemaValidator.HttpMethod httpMethod)
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory, metaSchema, userSchema, httpMethod);
    });
    // since the document was only validated against the user-schema and not the enterprise-user-extension schema
    // the extension attribute should not be present in the result
    Assertions.assertNull(validatedDocument.get(AttributeNames.ID));
    Assertions.assertNull(validatedDocument.get(AttributeNames.DISPLAY));
    Assertions.assertNull(validatedDocument.get(AttributeNames.GROUPS));
  }

  /**
   * will test that simple arrays will also be handled successfully
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "value1"})
  public void testValidationWithSimpleArrayNode(String value)
  {
    value = StringUtils.stripToNull(value);
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    final String attributeName = "simpleArray";
    JsonNode uniqueArray = JsonHelper.readJsonDocument(getAttributeString(attributeName,
                                                                          Type.STRING,
                                                                          true,
                                                                          true,
                                                                          true,
                                                                          Mutability.READ_WRITE,
                                                                          Returned.ALWAYS,
                                                                          Uniqueness.NONE));
    JsonNode attributes = JsonHelper.getArrayAttribute(metaSchemaNode, AttributeNames.ATTRIBUTES).get();
    JsonHelper.addAttributeToArray(attributes, uniqueArray);

    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    ArrayNode arrayNode = new ArrayNode(JsonNodeFactory.instance);
    Optional.ofNullable(value).ifPresent(arrayNode::add);
    JsonHelper.addAttribute(userSchema, attributeName, arrayNode);
    Schema metaSchema = new Schema(metaSchemaNode);
    Assertions.assertDoesNotThrow(() -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                                   metaSchema,
                                                                                   userSchema,
                                                                                   SchemaValidator.HttpMethod.POST));
  }

  /**
   * this test will verify that the validation fails if an array with a uniqueness of server or global has
   * duplicate values
   */
  @ParameterizedTest
  @ValueSource(strings = {"SERVER", "GLOBAL"})
  public void testDuplicateValueOnUniqueMultivaluedAttribute(Uniqueness uniqueness)
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    final String attributeName = "uniqueArray";
    JsonNode uniqueArray = JsonHelper.readJsonDocument(getAttributeString(attributeName,
                                                                          Type.STRING,
                                                                          true,
                                                                          true,
                                                                          true,
                                                                          Mutability.READ_WRITE,
                                                                          Returned.ALWAYS,
                                                                          uniqueness));
    JsonNode attributes = JsonHelper.getArrayAttribute(metaSchemaNode, AttributeNames.ATTRIBUTES).get();
    JsonHelper.addAttributeToArray(attributes, uniqueArray);

    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    ArrayNode arrayNode = new ArrayNode(JsonNodeFactory.instance);
    // add the same value twice
    arrayNode.add(attributeName);
    arrayNode.add(attributeName);
    JsonHelper.addAttribute(userSchema, attributeName, arrayNode);

    Schema metaSchema = new Schema(metaSchemaNode);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                             metaSchema,
                                                                             userSchema,
                                                                             SchemaValidator.HttpMethod.POST));
  }

  /**
   * this test will verify that the validation fails if an complex array with a uniqueness of server or global
   * has duplicate values<br>
   * the test will change the uniqueness of the emails-attribute and will then add a duplicate entry to the
   * emails-attribute
   */
  @ParameterizedTest
  @ValueSource(strings = {"SERVER", "GLOBAL"})
  public void testDuplicateValueOnUniqueComplexMultivaluedAttribute(Uniqueness uniqueness)
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode,
                            AttributeNames.EMAILS,
                            null,
                            null,
                            null,
                            uniqueness,
                            null,
                            null,
                            null,
                            null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    // @formatter:off
    final String email = "{" +
                         "    \"value\": \"goldfish@germany.de\",\n" +
                         "    \"type\": \"work\"" +
                         "}";
    // @formatter:on
    JsonNode emailNode = JsonHelper.readJsonDocument(email);
    ArrayNode emailArray = JsonHelper.getArrayAttribute(userSchema, AttributeNames.EMAILS).get();
    emailArray.add(emailNode);
    emailArray.add(emailNode);
    Schema metaSchema = new Schema(metaSchemaNode);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                             metaSchema,
                                                                             userSchema,
                                                                             SchemaValidator.HttpMethod.POST));
  }

  /**
   * This test will make sure that an exception is thrown if a multivalued complex type contains several primary
   * attributes
   */
  @Test
  public void testFailOnSeveralPrimaryMultivaluedComplexTypeValues()
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    // @formatter:off
    final String email = "{" +
                         "    \"value\": \"goldfish@germany.de\",\n" +
                         "    \"type\": \"work\",\n" +
                         "    \"primary\": true" +
                         "}";
    // @formatter:on
    JsonNode emailNode = JsonHelper.readJsonDocument(email);
    ArrayNode emailArray = JsonHelper.getArrayAttribute(userSchema, AttributeNames.EMAILS).get();
    emailArray.add(emailNode);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                             metaSchema,
                                                                             userSchema,
                                                                             SchemaValidator.HttpMethod.POST));
  }

  /**
   * will test that an attribute with returned-value never will not be returned from the server
   */
  @Test
  public void testReturnValueWithReturnedValueNever()
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode,
                            AttributeNames.EXTERNAL_ID,
                            null,
                            null,
                            Returned.NEVER,
                            null,
                            null,
                            null,
                            null,
                            null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory, metaSchema, userSchema);
    });
    Assertions.assertFalse(JsonHelper.getSimpleAttribute(validatedDocument, AttributeNames.EXTERNAL_ID).isPresent());
  }

  /**
   * will test that the validation fails if the schema reference within the schemas attribute of the document is
   * unknown
   */
  @Test
  public void testDocumentDoesNotContainMetaSchemaId()
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    ArrayNode schemas = JsonHelper.getArrayAttribute(userSchema, AttributeNames.SCHEMAS).get();
    schemas.removeAll();
    schemas.add("urn:some:unknown:id:reference");
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                             metaSchema,
                                                                             userSchema,
                                                                             SchemaValidator.HttpMethod.POST));
  }

  /**
   * tests that an exception is thrown if a required immutable or a wrtiteOnly attribute is not present on a
   * creation request
   */
  @ParameterizedTest
  @ValueSource(strings = {"IMMUTABLE", "WRITE_ONLY"})
  public void testMissingRequiredAttributesOnCreationRequest(Mutability mutability)
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode,
                            AttributeNames.USER_NAME,
                            null,
                            mutability,
                            Returned.NEVER,
                            null,
                            null,
                            true,
                            null,
                            null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    JsonHelper.removeAttribute(userSchema, AttributeNames.USER_NAME);

    Schema metaSchema = new Schema(metaSchemaNode);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                             metaSchema,
                                                                             userSchema,
                                                                             SchemaValidator.HttpMethod.POST));
  }

  /**
   * will verify that an exception is thrown if an attribute is of another type as declared in the schema. This
   * explicit test changes the username into an integer type but the attribute in the document will send a
   * string-username
   */
  @Test
  public void testValidationWithIncorrectAttributeType()
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode,
                            AttributeNames.USER_NAME,
                            Type.INTEGER,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    Schema metaSchema = new Schema(metaSchemaNode);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                             metaSchema,
                                                                             userSchema,
                                                                             SchemaValidator.HttpMethod.POST));
  }

  /**
   * will verify that the validation of an integer attribute type works successfully
   */
  @Test
  public void testValidationWithIntAttribute()
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode,
                            AttributeNames.USER_NAME,
                            Type.INTEGER,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    JsonHelper.addAttribute(userSchema, AttributeNames.USER_NAME, new IntNode(Integer.MAX_VALUE));

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                        metaSchema,
                                                        userSchema,
                                                        SchemaValidator.HttpMethod.POST);
    });
    Assertions.assertEquals(Integer.MAX_VALUE,
                            JsonHelper.getSimpleAttribute(validatedDocument, AttributeNames.USER_NAME, Integer.class)
                                      .get());
  }

  /**
   * will verify that the validation of a decimal (double) attribute type works successfully
   */
  @Test
  public void testValidationWithDecimalAttribute()
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode,
                            AttributeNames.USER_NAME,
                            Type.DECIMAL,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    JsonHelper.addAttribute(userSchema, AttributeNames.USER_NAME, new DoubleNode(Double.MAX_VALUE));
    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                        metaSchema,
                                                        userSchema,
                                                        SchemaValidator.HttpMethod.POST);
    });
    Assertions.assertEquals(Double.MAX_VALUE,
                            JsonHelper.getSimpleAttribute(validatedDocument, AttributeNames.USER_NAME, Double.class)
                                      .get());
  }

  /**
   * this test will verify that a required missing attribute will cause an exception if it has been set to a
   * jsonNull value
   */
  @Test
  public void testValidationWithJsonNullValue()
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    JsonHelper.addAttribute(userSchema, AttributeNames.USER_NAME, NullNode.instance);
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                             metaSchema,
                                                                             userSchema,
                                                                             SchemaValidator.HttpMethod.POST));
    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                                              metaSchema,
                                                                              userSchema));
  }

  /**
   * this test will verify that simple values that are sent on multivalued attributes are explicitly converted
   * into arrays. So it is allowed to send simple single values on multivalued types
   */
  @Test
  public void testUseSimpleNodeTypeOnMultiValuedAttribute()
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode, AttributeNames.USER_NAME, null, null, null, null, true, null, null, null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                        metaSchema,
                                                        userSchema,
                                                        SchemaValidator.HttpMethod.POST);
    });

    JsonNode userName = validatedDocument.get(AttributeNames.USER_NAME);
    Assertions.assertNotNull(userName);
    Assertions.assertTrue(userName.isArray());
    Assertions.assertEquals(1, userName.size());
  }

  /**
   * this test will verify that a {@link de.gold.scim.constants.enums.ReferenceTypes#RESOURCE} referenceType is
   * successfully verified if the given resourceType name is registered in the {@link ResourceTypeFactory}
   */
  @Test
  public void testResourceReferenceIsUsedAndResourceWasRegistered()
  {
    JsonNode userResourceType = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);
    JsonNode userResourceSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    JsonNode enterpriseUserExtension = JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON);
    resourceTypeFactory.registerResourceType(null, userResourceType, userResourceSchema, enterpriseUserExtension);

    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE_ENTERPRISE);
    JsonNode enterpriseUser = JsonHelper.getObjectAttribute(userSchema, SchemaUris.ENTERPRISE_USER_URI).get();

    Schema enterpriseSchema = new Schema(enterpriseUserExtension);
    Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateExtensionForRequest(resourceTypeFactory,
                                                         enterpriseSchema,
                                                         enterpriseUser,
                                                         SchemaValidator.HttpMethod.POST);
    });
  }

  /**
   * this test will verify that an exception is thrown if a
   * {@link de.gold.scim.constants.enums.ReferenceTypes#RESOURCE} referenceType is not registered in the
   * {@link ResourceTypeFactory}
   */
  @Test
  public void testResourceReferenceIsUsedAndResourceWasNOTRegistered()
  {
    Schema metaSchema = new Schema(JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON));
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE_ENTERPRISE);
    JsonNode enterpriseUser = JsonHelper.getObjectAttribute(userSchema, SchemaUris.ENTERPRISE_USER_URI).get();
    Assertions.assertThrows(DocumentValidationException.class, () -> {
      SchemaValidator.validateExtensionForRequest(ResourceTypeFactory.getUnitTestInstance(),
                                                  metaSchema,
                                                  enterpriseUser,
                                                  SchemaValidator.HttpMethod.POST);
    });
  }

  /**
   * will check that an exception is thrown if a required extension is missing
   */
  @Test
  public void testBadRequestOnMissingRequiredExtension()
  {
    JsonNode userResourceType = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);
    JsonNode userResourceSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    JsonNode enterpriseUserExtension = JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON);
    JsonNode schemaExtensions = JsonHelper.getArrayAttribute(userResourceType, AttributeNames.SCHEMA_EXTENSIONS).get();
    // sets the enterprise user extension to required
    for ( JsonNode schemaExtension : schemaExtensions )
    {
      JsonHelper.addAttribute(schemaExtension, AttributeNames.REQUIRED, BooleanNode.valueOf(true));
    }
    ResourceType resourceType = resourceTypeFactory.registerResourceType(null,
                                                                         userResourceType,
                                                                         userResourceSchema,
                                                                         enterpriseUserExtension);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Assertions.assertThrows(DocumentValidationException.class,
                            () -> SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                                             resourceType,
                                                                             userSchema,
                                                                             SchemaValidator.HttpMethod.POST));
  }

  /**
   * this test will have a always returned attribute that is not present in the response because it is not a
   * required attribute. For this reason the validation will not throw an exception but print a log message.
   * This test is for code coverage and checking for NullPointerExceptions or similar
   */
  @ParameterizedTest
  @ValueSource(strings = {"ALWAYS", "REQUEST", "DEFAULT"})
  public void testNonPresentReturnedAttribute(Returned returned)
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode,
                            AttributeNames.NICK_NAME,
                            null,
                            null,
                            returned,
                            null,
                            null,
                            null,
                            null,
                            null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         metaSchema,
                                                         userSchema,
                                                         null,
                                                         AttributeNames.NICK_NAME,
                                                         null);
    });

    JsonNode nickName = validatedDocument.get(AttributeNames.NICK_NAME);
    Assertions.assertNull(nickName);
    JsonNode id = validatedDocument.get(AttributeNames.ID);
    Assertions.assertNotNull(id);
    JsonNode userName = validatedDocument.get(AttributeNames.USER_NAME);
    Assertions.assertNull(userName);
    JsonNode externalId = validatedDocument.get(AttributeNames.EXTERNAL_ID);
    Assertions.assertNull(externalId);
    JsonNode description = validatedDocument.get(AttributeNames.DESCRIPTION);
    Assertions.assertNull(description);
    JsonNode display = validatedDocument.get(AttributeNames.DISPLAY);
    Assertions.assertNull(display);
    JsonNode phoneNumbers = validatedDocument.get(AttributeNames.PHONE_NUMBERS);
    Assertions.assertNull(phoneNumbers);
    JsonNode emails = validatedDocument.get(AttributeNames.EMAILS);
    Assertions.assertNull(emails);
    JsonNode groups = validatedDocument.get(AttributeNames.GROUPS);
    Assertions.assertNull(groups);
    JsonNode roles = validatedDocument.get(AttributeNames.ROLES);
    Assertions.assertNull(roles);
  }

  /**
   * this test will verify that attributes whos returned value is {@link Returned#REQUEST} will not be returned
   * until they have been requested explicitly
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.USER_NAME, AttributeNames.NAME, AttributeNames.DISPLAY_NAME,
                          AttributeNames.EMAILS, AttributeNames.PHONE_NUMBERS,
                          AttributeNames.PHONE_NUMBERS + "." + AttributeNames.VALUE,
                          AttributeNames.NAME + "." + AttributeNames.GIVEN_NAME})
  public void testDoNotReturnRequestAttributes(String attributeName)
  {
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode, attributeName, null, null, Returned.REQUEST, null, null, null, null, null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory, metaSchema, userSchema, null, null, null);
    });
    String[] attributeNameParts = attributeName.split("\\.");
    if (attributeNameParts.length == 1)
    {
      Assertions.assertNull(validatedDocument.get(attributeName));
    }
    else
    {
      JsonNode complexAttribute = validatedDocument.get(attributeNameParts[0]);
      Assertions.assertNotNull(complexAttribute);
      Assertions.assertNull(complexAttribute.get(attributeNameParts[1]));
    }
  }

  /**
   * This test will verify that an attribute with a returned value of request is returned if the attribute was
   * on the request. Meaning the attribute was set during creation or modified on a PUT or PATCH request.<br>
   * from RFC7643 chapter 7
   *
   * <pre>
   *   request  The attribute is returned in response to any PUT,
   *             POST, or PATCH operations if the attribute was specified by
   *             the client (for example, the attribute was modified).  The
   *             attribute is returned in a SCIM query operation only if
   *             specified in the "attributes" parameter.
   * </pre>
   */
  @Test
  public void testRequestAttributeIsReturnedAfterPutPostOrPatchRequest()
  {
    final String attributeName = AttributeNames.USER_NAME;
    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode, attributeName, null, null, Returned.REQUEST, null, null, null, null, null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode validatedRequestDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                        metaSchema,
                                                        userSchema,
                                                        SchemaValidator.HttpMethod.POST);
    });

    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         metaSchema,
                                                         userSchema,
                                                         validatedRequestDocument,
                                                         null,
                                                         null);
    });

    Assertions.assertNotNull(validatedDocument.get(attributeName));
    Assertions.assertNotNull(validatedDocument.get(AttributeNames.ID));
  }

  /**
   * This test will verify that an attribute is also returned if the full URI of the attribute name was used in
   * the attributes parameter<br>
   * from RFC7643 chapter 7
   *
   * <pre>
   *   request  The attribute is returned in response to any PUT,
   *             POST, or PATCH operations if the attribute was specified by
   *             the client (for example, the attribute was modified).  The
   *             attribute is returned in a SCIM query operation only if
   *             specified in the "attributes" parameter.
   * </pre>
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.USER_NAME, AttributeNames.DISPLAY_NAME, AttributeNames.EXTERNAL_ID,
                          AttributeNames.NAME, AttributeNames.EMAILS,
                          AttributeNames.NAME + "." + AttributeNames.GIVEN_NAME,
                          AttributeNames.NAME + "." + AttributeNames.MIDDLE_NAME})
  public void testAttributeIsReturnedIfFullUriNameIsUsedOnAttributesParameter(String attributeName)
  {
    final String fullName = SchemaUris.USER_URI + ":" + attributeName;

    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode, attributeName, null, null, Returned.REQUEST, null, null, null, null, null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode missingAttributeUser = userSchema.deepCopy();
    JsonHelper.removeAttribute(missingAttributeUser, attributeName);
    JsonNode validatedRequestDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                        metaSchema,
                                                        missingAttributeUser,
                                                        SchemaValidator.HttpMethod.PUT);
    });

    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         metaSchema,
                                                         userSchema,
                                                         validatedRequestDocument,
                                                         fullName,
                                                         null);
    });

    String[] attributeNameParts = attributeName.split("\\.");
    if (attributeNameParts.length == 1)
    {
      Assertions.assertNotNull(validatedDocument.get(attributeName));
    }
    else
    {
      JsonNode complexAttribute = validatedDocument.get(attributeNameParts[0]);
      Assertions.assertNotNull(complexAttribute);
      Assertions.assertNotNull(complexAttribute.get(attributeNameParts[1]));
    }
    Assertions.assertNotNull(validatedDocument.get(AttributeNames.ID));
  }

  /**
   * This test will verify that an attribute is also returned if the short name of the attribute was used in the
   * attributes parameter<br>
   * from RFC7643 chapter 7
   *
   * <pre>
   *   request  The attribute is returned in response to any PUT,
   *             POST, or PATCH operations if the attribute was specified by
   *             the client (for example, the attribute was modified).  The
   *             attribute is returned in a SCIM query operation only if
   *             specified in the "attributes" parameter.
   * </pre>
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.USER_NAME, AttributeNames.DISPLAY_NAME, AttributeNames.EXTERNAL_ID,
                          AttributeNames.NAME, AttributeNames.EMAILS,
                          AttributeNames.NAME + "." + AttributeNames.GIVEN_NAME,
                          AttributeNames.NAME + "." + AttributeNames.MIDDLE_NAME})
  public void testAttributeIsReturnedIfShortNameIsUsedOnAttributesParameter(String attributeName)
  {
    final String fullName = attributeName;

    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode, attributeName, null, null, Returned.REQUEST, null, null, null, null, null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode missingAttributeUser = userSchema.deepCopy();
    JsonHelper.removeAttribute(missingAttributeUser, attributeName);
    JsonNode validatedRequestDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                        metaSchema,
                                                        missingAttributeUser,
                                                        SchemaValidator.HttpMethod.PUT);
    });

    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         metaSchema,
                                                         userSchema,
                                                         validatedRequestDocument,
                                                         fullName,
                                                         null);
    });

    String[] attributeNameParts = attributeName.split("\\.");
    if (attributeNameParts.length == 1)
    {
      Assertions.assertNotNull(validatedDocument.get(attributeName));
    }
    else
    {
      JsonNode complexAttribute = validatedDocument.get(attributeNameParts[0]);
      Assertions.assertNotNull(complexAttribute);
      Assertions.assertNotNull(complexAttribute.get(attributeNameParts[1]));
    }
    Assertions.assertNotNull(validatedDocument.get(AttributeNames.ID));
  }

  /**
   * This test will verify that an attribute is also returned if the URI of the resource was used in the
   * attributes parameter<br>
   * from RFC7643 chapter 7
   *
   * <pre>
   *   request  The attribute is returned in response to any PUT,
   *             POST, or PATCH operations if the attribute was specified by
   *             the client (for example, the attribute was modified).  The
   *             attribute is returned in a SCIM query operation only if
   *             specified in the "attributes" parameter.
   * </pre>
   */
  @Test
  public void testAttributeIsReturnedIfResourceUriIsUsedOnAttributesParameter()
  {
    final String attributeName = AttributeNames.NAME;

    JsonNode metaSchemaNode = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    modifyAttributeMetaData(metaSchemaNode, attributeName, null, null, Returned.REQUEST, null, null, null, null, null);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);

    Schema metaSchema = new Schema(metaSchemaNode);
    JsonNode missingAttributeUser = userSchema.deepCopy();
    JsonHelper.removeAttribute(missingAttributeUser, attributeName);
    JsonNode validatedRequestDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForRequest(resourceTypeFactory,
                                                        metaSchema,
                                                        missingAttributeUser,
                                                        SchemaValidator.HttpMethod.PUT);
    });

    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         metaSchema,
                                                         userSchema,
                                                         validatedRequestDocument,
                                                         SchemaUris.USER_URI,
                                                         null);
    });

    Assertions.assertNotNull(validatedDocument.get(attributeName));
    Assertions.assertNotNull(validatedDocument.get(AttributeNames.ID));
  }

  /**
   * same as {@link #testRequestAttributeIsReturnedAfterPutPostOrPatchRequest} but this time the check is for a
   * schema extension and not the main resource
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.EMPLOYEE_NUMBER, AttributeNames.COST_CENTER, AttributeNames.ORGANIZATION,
                          AttributeNames.DIVISION, AttributeNames.DEPARTMENT, AttributeNames.MANAGER})
  public void testRequestAttributeIsReturnedAfterPutPostOrPatchRequestForExtension(String attributeName)
  {
    JsonNode userResourceType = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);
    JsonNode userResourceSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    JsonNode enterpriseUserExtension = JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON);
    modifyAttributeMetaData(enterpriseUserExtension,
                            attributeName,
                            null,
                            null,
                            Returned.REQUEST,
                            null,
                            null,
                            null,
                            null,
                            null);
    ResourceType resourceType = resourceTypeFactory.registerResourceType(null,
                                                                         userResourceType,
                                                                         userResourceSchema,
                                                                         enterpriseUserExtension);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE_ENTERPRISE);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         resourceType,
                                                         userSchema,
                                                         null,
                                                         null,
                                                         null);
    });
    JsonNode enterpriseUser = validatedDocument.get(SchemaUris.ENTERPRISE_USER_URI);
    Assertions.assertNotNull(enterpriseUser);
    JsonNode attribute = enterpriseUser.get(attributeName);
    Assertions.assertNull(attribute);
  }

  /**
   * this test shall verify that the attributes from an extension are removed if they have a returned value of
   * default or request and they are not defined in the attributes parameter
   */
  @ParameterizedTest
  @ValueSource(strings = {AttributeNames.EMPLOYEE_NUMBER, AttributeNames.COST_CENTER, AttributeNames.ORGANIZATION,
                          AttributeNames.DIVISION, AttributeNames.DEPARTMENT, AttributeNames.MANAGER})
  public void testRemoveAttributesFromResponseOnExtension(String attributeName)
  {
    JsonNode userResourceType = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);
    JsonNode userResourceSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    JsonNode enterpriseUserExtension = JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON);
    modifyAttributeMetaData(enterpriseUserExtension,
                            attributeName,
                            null,
                            null,
                            Returned.REQUEST,
                            null,
                            null,
                            null,
                            null,
                            null);
    ResourceType resourceType = resourceTypeFactory.registerResourceType(null,
                                                                         userResourceType,
                                                                         userResourceSchema,
                                                                         enterpriseUserExtension);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE_ENTERPRISE);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         resourceType,
                                                         userSchema,
                                                         null,
                                                         attributeName,
                                                         null);
    });
    JsonNode enterpriseUser = validatedDocument.get(SchemaUris.ENTERPRISE_USER_URI);
    Assertions.assertNotNull(enterpriseUser);
    JsonNode attribute = enterpriseUser.get(attributeName);
    Assertions.assertNotNull(attribute);
  }

  /**
   * this test will provoke that all attributes from the enterprise user extension are removed from the response
   * which causes the extension itself to be removed. In such a case the enterprise schema uri must also be
   * removed from the schemas-attribute of the main document
   */
  @Test
  public void testRemoveAttributesFromResponseOnExtension()
  {
    JsonNode userResourceType = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);
    JsonNode userResourceSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    JsonNode enterpriseUserExtension = JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON);
    Arrays.asList(AttributeNames.EMPLOYEE_NUMBER,
                  AttributeNames.COST_CENTER,
                  AttributeNames.ORGANIZATION,
                  AttributeNames.DIVISION,
                  AttributeNames.DEPARTMENT,
                  AttributeNames.MANAGER)
          .forEach(attributeName -> {

            modifyAttributeMetaData(enterpriseUserExtension,
                                    attributeName,
                                    null,
                                    null,
                                    Returned.REQUEST,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null);
          });
    ResourceType resourceType = resourceTypeFactory.registerResourceType(null,
                                                                         userResourceType,
                                                                         userResourceSchema,
                                                                         enterpriseUserExtension);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE_ENTERPRISE);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         resourceType,
                                                         userSchema,
                                                         null,
                                                         null,
                                                         null);
    });
    JsonNode enterpriseUser = validatedDocument.get(SchemaUris.ENTERPRISE_USER_URI);
    Assertions.assertNull(enterpriseUser);
    List<String> schemas = JsonHelper.getSimpleAttributeArray(validatedDocument, AttributeNames.SCHEMAS).get();
    MatcherAssert.assertThat(schemas, Matchers.not(Matchers.hasItem(SchemaUris.ENTERPRISE_USER_URI)));
  }

  /**
   * Verifies that excluded attributes are removed from extensions
   */
  @Test
  public void testExcludedAttributes()
  {
    JsonNode userResourceType = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);
    JsonNode userResourceSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    JsonNode enterpriseUserExtension = JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON);
    List<String> excludedList = Arrays.asList(AttributeNames.USER_NAME,
                                              AttributeNames.DISPLAY_NAME,
                                              AttributeNames.EXTERNAL_ID,
                                              AttributeNames.EMAILS,
                                              AttributeNames.NAME + "." + AttributeNames.GIVEN_NAME,
                                              AttributeNames.NAME + "." + AttributeNames.MIDDLE_NAME);
    String excluded = String.join(",", excludedList);
    ResourceType resourceType = resourceTypeFactory.registerResourceType(null,
                                                                         userResourceType,
                                                                         userResourceSchema,
                                                                         enterpriseUserExtension);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         resourceType,
                                                         userSchema,
                                                         null,
                                                         null,
                                                         excluded);
    });
    for ( String attributeName : excludedList )
    {
      String[] attributeNameParts = attributeName.split("\\.");
      if (attributeNameParts.length == 1)
      {
        Assertions.assertNull(validatedDocument.get(attributeName), attributeName);
      }
      else
      {
        JsonNode complexAttribute = validatedDocument.get(attributeNameParts[0]);
        Assertions.assertNotNull(complexAttribute, attributeName);
        Assertions.assertNull(complexAttribute.get(attributeNameParts[1]), attributeName);
      }
    }
  }

  /**
   * Verifies that complex attributes are removed if mentioned on first level e.g. "name"
   */
  @Test
  public void testExcludedNameAttribute()
  {
    JsonNode userResourceType = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);
    JsonNode userResourceSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    JsonNode enterpriseUserExtension = JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON);
    ResourceType resourceType = resourceTypeFactory.registerResourceType(null,
                                                                         userResourceType,
                                                                         userResourceSchema,
                                                                         enterpriseUserExtension);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         resourceType,
                                                         userSchema,
                                                         null,
                                                         null,
                                                         AttributeNames.NAME);
    });
    Assertions.assertNull(validatedDocument.get(AttributeNames.NAME));
  }

  /**
   * Verifies that excluded attributes are removed from extensions
   */
  @Test
  public void testExcludedAttributesOnExtension()
  {
    JsonNode userResourceType = JsonHelper.loadJsonDocument(ClassPathReferences.USER_RESOURCE_TYPE_JSON);
    JsonNode userResourceSchema = JsonHelper.loadJsonDocument(ClassPathReferences.USER_SCHEMA_JSON);
    JsonNode enterpriseUserExtension = JsonHelper.loadJsonDocument(ClassPathReferences.ENTERPRISE_USER_SCHEMA_JSON);
    String excluded = String.join(",",
                                  AttributeNames.EMPLOYEE_NUMBER,
                                  AttributeNames.COST_CENTER,
                                  AttributeNames.ORGANIZATION,
                                  AttributeNames.DIVISION,
                                  AttributeNames.DEPARTMENT,
                                  AttributeNames.MANAGER);
    ResourceType resourceType = resourceTypeFactory.registerResourceType(null,
                                                                         userResourceType,
                                                                         userResourceSchema,
                                                                         enterpriseUserExtension);
    JsonNode userSchema = JsonHelper.loadJsonDocument(USER_RESOURCE_ENTERPRISE);
    JsonNode validatedDocument = Assertions.assertDoesNotThrow(() -> {
      return SchemaValidator.validateDocumentForResponse(resourceTypeFactory,
                                                         resourceType,
                                                         userSchema,
                                                         null,
                                                         null,
                                                         excluded);
    });
    JsonNode enterpriseUser = validatedDocument.get(SchemaUris.ENTERPRISE_USER_URI);
    Assertions.assertNull(enterpriseUser);
    List<String> schemas = JsonHelper.getSimpleAttributeArray(validatedDocument, AttributeNames.SCHEMAS).get();
    MatcherAssert.assertThat(schemas, Matchers.not(Matchers.hasItem(SchemaUris.ENTERPRISE_USER_URI)));
  }

  /**
   * this method extracts the the attribute with the given name from the meta schema and modifies its attributes
   * with the given values
   *
   * @param metaSchema the meta schema that must contain an attribute with the given attributeName parameter
   * @param attributeName the name of the attribute that will should be modified. This attribute must exist
   */
  private void modifyAttributeMetaData(JsonNode metaSchema,
                                       String attributeName,
                                       Type type,
                                       Mutability mutability,
                                       Returned returned,
                                       Uniqueness uniqueness,
                                       Boolean multiValued,
                                       Boolean required,
                                       Boolean caseExact,
                                       List<String> canonicalTypes)
  {
    String[] attributeNameParts = attributeName.split("\\.");
    JsonNode attributes = JsonHelper.getArrayAttribute(metaSchema, AttributeNames.ATTRIBUTES).get();
    JsonNode attributeDefinition = null;
    for ( JsonNode attribute : attributes )
    {
      String name = JsonHelper.getSimpleAttribute(attribute, AttributeNames.NAME).get();
      if (name.equals(attributeNameParts[0]))
      {
        attributeDefinition = attribute;
        break;
      }
    }
    if (attributeNameParts.length == 2)
    {
      JsonNode subAttributes = JsonHelper.getArrayAttribute(attributeDefinition, AttributeNames.SUB_ATTRIBUTES).get();
      for ( JsonNode attribute : subAttributes )
      {
        String name = JsonHelper.getSimpleAttribute(attribute, AttributeNames.NAME).get();
        if (name.equals(attributeNameParts[1]))
        {
          attributeDefinition = attribute;
          break;
        }
      }
    }
    Assertions.assertNotNull(attributeDefinition);
    JsonNode finalAttributeDefinition = attributeDefinition;
    Optional.ofNullable(type).ifPresent(t -> {
      JsonHelper.addAttribute(finalAttributeDefinition, AttributeNames.TYPE, new TextNode(t.getValue()));
    });
    Optional.ofNullable(mutability).ifPresent(m -> {
      JsonHelper.addAttribute(finalAttributeDefinition, AttributeNames.MUTABILITY, new TextNode(m.getValue()));
    });
    Optional.ofNullable(returned).ifPresent(r -> {
      JsonHelper.addAttribute(finalAttributeDefinition, AttributeNames.RETURNED, new TextNode(r.getValue()));
    });
    Optional.ofNullable(uniqueness).ifPresent(u -> {
      JsonHelper.addAttribute(finalAttributeDefinition, AttributeNames.UNIQUENESS, new TextNode(u.getValue()));
    });
    Optional.ofNullable(multiValued).ifPresent(multi -> {
      JsonHelper.addAttribute(finalAttributeDefinition, AttributeNames.MULTI_VALUED, BooleanNode.valueOf(multi));
    });
    Optional.ofNullable(required).ifPresent(r -> {
      JsonHelper.addAttribute(finalAttributeDefinition, AttributeNames.REQUIRED, BooleanNode.valueOf(r));
    });
    Optional.ofNullable(caseExact).ifPresent(c -> {
      JsonHelper.addAttribute(finalAttributeDefinition, AttributeNames.CASE_EXACT, BooleanNode.valueOf(c));
    });
    Optional.ofNullable(canonicalTypes).ifPresent(canonical -> {
      ArrayNode arrayNode = new ArrayNode(JsonNodeFactory.instance);
      arrayNode.addAll(canonical.stream().map(TextNode::new).collect(Collectors.toList()));
      JsonHelper.addAttribute(finalAttributeDefinition, AttributeNames.REFERENCE_TYPES, arrayNode);
    });
  }

  private String getAttributeString(String name,
                                    Type type,
                                    boolean multiValued,
                                    boolean required,
                                    boolean caseExact,
                                    Mutability mutability,
                                    Returned returned,
                                    Uniqueness uniqueness)
  {
    // @formatter:off
    return "{" +
           "   \"name\": \"" + name + "\",\n" +
           "   \"type\": \"" + type.getValue() + "\",\n" +
           "   \"multiValued\": " + multiValued+ ",\n" +
           "   \"description\": \"some description\",\n" +
           "   \"required\": " + required + ",\n" +
           "   \"caseExact\": " + caseExact + ",\n" +
           "   \"mutability\": \"" + mutability.getValue() + "\",\n" +
           "   \"returned\": \"" + returned.getValue() + "\",\n" +
           "   \"uniqueness\": \"" + uniqueness.getValue() + "\"\n" +
           "}";
    // @formatter:on
  }
}
