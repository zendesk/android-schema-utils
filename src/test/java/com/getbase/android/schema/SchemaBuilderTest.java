package com.getbase.android.schema;

import static com.getbase.android.schema.TestUtils.EMPTY_MIGRATION;
import static com.getbase.android.schema.TestUtils.VALID_DOWNGRADE;
import static com.getbase.android.schema.TestUtils.release;
import static org.fest.assertions.Assertions.assertThat;

import com.getbase.android.schema.Schemas.AddColumn;
import com.getbase.android.schema.Schemas.TableDefinition;
import com.getbase.android.schema.Schemas.TableDowngrade;
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
            VALID_DOWNGRADE,
            VALID_DOWNGRADE
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
  public void shouldRejectDuplicateUpgrades() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .upgradeTo(1500, EMPTY_MIGRATION)
        .upgradeTo(1500, EMPTY_MIGRATION);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradesWithNotDescendingOffsets() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .downgradeTo(1500)
        .downgradeTo(1600);
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

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradeWithOffsetHigherThanLastUpgrade() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .upgradeTo(1500, EMPTY_MIGRATION)
        .downgradeTo(1600, VALID_DOWNGRADE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectUpgradeWithOffsetHigherThanLastDowngrade() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .downgradeTo(1500, VALID_DOWNGRADE)
        .upgradeTo(1600, EMPTY_MIGRATION);
  }

  @Test
  public void shouldAllowUpgradeWithTheSameOffsetAsTheLastDowngrade() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .downgradeTo(1500, VALID_DOWNGRADE)
        .upgradeTo(1500, EMPTY_MIGRATION);
  }

  @Test
  public void shouldAllowUpgradeWithTheSameOffsetInSectionsForDifferentReleaseMarkers() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .upgradeTo(42, EMPTY_MIGRATION)
        .release(release(1500))
        .upgradeTo(42, EMPTY_MIGRATION)
        .release(release(666));
  }

  @Test
  public void shouldAllowDowngradeWithTheSameOffsetInSectionsForDifferentReleaseMarkers() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .downgradeTo(42, VALID_DOWNGRADE)
        .release(release(1500))
        .downgradeTo(42, VALID_DOWNGRADE)
        .release(release(666));
  }

  @Test
  public void shouldAllowReleasesWithTheSameRevisionNumber() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .release(release(1500))
        .release(release(1500));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectReleaseWithRevisionNumberHigherThanTheLastRelease() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1500))
        .release(release(2900));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradeForVersionEqualToVersionOfTheNextRelease() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1600))
        .downgradeTo(100, VALID_DOWNGRADE)
        .release(release(1500));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradeForVersionEqualToVersionOfTheNextReleaseInInitialSchemaCase() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1500))
        .downgradeTo(1500, VALID_DOWNGRADE)
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradeWithNegativeOffset() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .downgradeTo(-1, VALID_DOWNGRADE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectUpgradeWithNegativeOffset() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .upgradeTo(-1, EMPTY_MIGRATION);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradeForVersionHigherThanVersionOfTheNextRelease() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1600))
        .downgradeTo(101, VALID_DOWNGRADE)
        .release(release(1500));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradeForVersionHigherThanVersionOfTheNextReleaseInInitialSchemaCase() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1500))
        .downgradeTo(2900, VALID_DOWNGRADE)
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectUpgradeWithZeroOffset() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .upgradeTo(0, EMPTY_MIGRATION);
  }

  @Test
  public void shouldAcceptUpgradeForVersionEqualToVersionOfTheNextRelease() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1600))
        .upgradeTo(100, EMPTY_MIGRATION)
        .release(release(1500));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectUpgradeForVersionHigherThanVersionOfTheNextRelease() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1600))
        .upgradeTo(101, EMPTY_MIGRATION)
        .release(release(1500));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectUpgradeFromInitialSchemaForVersionHigherThanVersionOfTheNextRelease() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1500))
        .upgradeTo(1600, EMPTY_MIGRATION)
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectDowngradeBetweenTwoReleaseMarkersWithTheSameVersion() throws Exception {
    Schemas.Builder
        .currentSchema(0)
        .release(release(1500))
        .downgradeTo(0, VALID_DOWNGRADE)
        .release(release(1500))
        .build();
  }

  private static final ImmutableList<AddColumn> LEGACY_COLUMNS = ImmutableList.of();

  public void shouldAllowUsingImmutableListOfAddColumnsInDowngradeDefinition() {
    Schemas.Builder
        .currentSchema(1,
            new TableDefinition("test", new AddColumn("id", "INTEGER"))
        )
        .downgradeTo(0, new TableDowngrade("test", LEGACY_COLUMNS))
        .build();
  }
}
