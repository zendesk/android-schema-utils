package com.getbase.android.schema;

import static com.getbase.android.schema.TestUtils.release;
import static org.fest.assertions.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

import com.getbase.android.schema.Schemas.Builder;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(reportSdk = 10, manifest = Config.NONE)
public class SchemasTest {

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void shouldCalculateCurrentRevisionNumberAsCurrentSchemaOffsetPlusMostRecentReleaseMarker() throws Exception {
    Schemas schemas = Builder
        .currentSchema(2900)
        .release(release(1500))
        .release(release(666))
        .build();

    assertThat(schemas.getCurrentRevisionNumber()).isEqualTo(4400);
  }

  @Test
  public void shouldUseCurrentSchemaOffsetAsCurrentRevisionNumberWhenThereAreNoReleaseMarkers() throws Exception {
    Schemas schemas = Builder
        .currentSchema(2900)
        .build();

    assertThat(schemas.getCurrentRevisionNumber()).isEqualTo(2900);
  }

  @Test
  public void shouldAllowGettingSchemaForPreviousVersion() throws Exception {
    Schemas db = Schemas.Builder
        .currentSchema(2900)
        .build();

    assertThat(db.getSchema(1500)).isNotNull();
  }

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldNotAllowGettingSchemaForVersionHigherThanCurrentVersion() throws Exception {
    expectedException.expectCause(is(CoreMatchers.<IllegalStateException>instanceOf(IllegalStateException.class)));

    Schemas db = Schemas.Builder
        .currentSchema(1500)
        .build();

    db.getSchema(2900);
  }
}
