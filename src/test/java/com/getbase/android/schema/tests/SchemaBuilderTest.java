package com.getbase.android.schema.tests;

import static org.fest.assertions.Assertions.assertThat;

import com.getbase.android.schema.Schemas;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(reportSdk = 10, manifest = Config.NONE)
public class SchemaBuilderTest {

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDuplicateTablesInCurrentSchema() throws Exception {
    Schemas.Builder
        .currentSchema(1500,
            new Schemas.TableDefinition("Deals", new Schemas.AddColumn("ID", "")),
            new Schemas.TableDefinition("Deals", new Schemas.AddColumn("ID", "")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDuplicateTablesInScopeOfSingleRevision() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .downgradeTo(1500,
            new Schemas.TableDowngrade("Deals",
                new Schemas.AddColumn("ID", "")
            ),
            new Schemas.TableDowngrade("Deals",
                new Schemas.AddColumn("ID", "")
            )
        );
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradesForRevisionHigherThanCurrentRevision() throws Exception {
    Schemas.Builder
        .currentSchema(1500)
        .downgradeTo(2900);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradesForRevisionEqualToCurrentRevision() throws Exception {
    Schemas.Builder
        .currentSchema(1500)
        .downgradeTo(1500);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDuplicateRevisions() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .downgradeTo(1500)
        .downgradeTo(1500);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDuplicateColumnsInCurrentSchema() throws Exception {
    new Schemas.TableDefinition("Deals",
        new Schemas.AddColumn("ID", ""),
        new Schemas.AddColumn("ID", "")
    );
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDuplicateColumnsInOldSchema() throws Exception {
    new Schemas.TableDowngrade("Deals",
        new Schemas.AddColumn("ID", ""),
        new Schemas.AddColumn("ID", "")
    );
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDuplicateConstraintsInCurrentSchema() throws Exception {
    new Schemas.TableDefinition("Deals",
        new Schemas.AddConstraint("X"),
        new Schemas.AddConstraint("X")
    );
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDuplicateConstraintsInOldSchema() throws Exception {
    new Schemas.TableDowngrade("Deals",
        new Schemas.AddConstraint("X"),
        new Schemas.AddConstraint("X")
    );
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDropAndAddForTheSameColumn() throws Exception {
    new Schemas.TableDowngrade("Deals",
        new Schemas.AddColumn("ID", ""),
        new Schemas.DropColumn("ID")
    );
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDroppingTheSameColumnTwice() throws Exception {
    new Schemas.TableDowngrade("Deals",
        new Schemas.DropColumn("ID"),
        new Schemas.DropColumn("ID")
    );
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDropAndAddForTheSameConstraint() throws Exception {
    new Schemas.TableDowngrade("Deals",
        new Schemas.AddConstraint("X"),
        new Schemas.DropConstraint("X")
    );
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDroppingTheSameConstraintTwice() throws Exception {
    new Schemas.TableDowngrade("Deals",
        new Schemas.DropConstraint("ID"),
        new Schemas.DropConstraint("ID")
    );
  }

  @Test
  public void shouldRejectDroppingTheTableAndDoingAnythingElse() throws Exception {
    List<Schemas.TableDowngradeOperation> operations = ImmutableList.of(
        new Schemas.DropTable(),
        new Schemas.AddColumn("ID", ""),
        new Schemas.AddConstraint("X"),
        new Schemas.DropColumn("ID"),
        new Schemas.DropConstraint("ID")
    );

    int count = 0;
    for (Schemas.TableDowngradeOperation operation : operations) {
      try {
        new Schemas.TableDowngrade("Deals",
            new Schemas.DropTable(),
            operation
        );
      } catch (IllegalArgumentException e) {
        ++count;
      }
    }

    assertThat(count).isEqualTo(operations.size());
  }
}
