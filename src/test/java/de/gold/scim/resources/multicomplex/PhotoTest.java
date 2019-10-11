package de.gold.scim.resources.multicomplex;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * author Pascal Knueppel <br>
 * created at: 11.10.2019 - 23:55 <br>
 * <br>
 */
public class PhotoTest
{

  /**
   * verifies that no exception is thrown on empty builder creation
   */
  @Test
  public void testUseBuilderWithoutParameters()
  {
    Photo instance = Assertions.assertDoesNotThrow(() -> Photo.builder().build());
    Assertions.assertTrue(instance.isEmpty());
  }

  /**
   * will test that a new instance has no attributes at all
   */
  @Test
  public void testCleanObjectCreation()
  {
    Assertions.assertTrue(new Photo().isEmpty());
  }

  /**
   * will test if the attributes are correctly added into the json object
   */
  @Test
  public void testSetAndGetAttributes()
  {
    final String value = UUID.randomUUID().toString();
    final String display = UUID.randomUUID().toString();
    final boolean primary = true;
    final String type = UUID.randomUUID().toString();
    final String ref = UUID.randomUUID().toString();
    Photo photo = Photo.builder().value(value).display(display).primary(primary).type(type).ref(ref).build();
    Assertions.assertEquals(value, photo.getValue().get());
    Assertions.assertEquals(display, photo.getDisplay().get());
    Assertions.assertEquals(primary, photo.isPrimary());
    Assertions.assertEquals(type, photo.getScimType().get());
    Assertions.assertEquals(ref, photo.getRef().get());
  }
}
