package com.getbase.android.schema.tests;

import static com.getbase.android.schema.Migrations.auto;

import com.getbase.android.schema.Migration;
import com.getbase.android.schema.Schemas;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(reportSdk = 10, manifest = Config.NONE)
public class InvalidSchemasTest {

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectToBuildUndefinedTable() throws Exception {
    Schemas db = Schemas.Builder
        .currentSchema(2900)
        .build();

    db.getCurrentSchema().getCreateTableStatement("Deals");
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectToBuildDroppedTable() throws Exception {
    Schemas db = Schemas.Builder
        .currentSchema(2900, new Schemas.TableDefinition("Deals",
            new Schemas.AddColumn("id", "")))
        .downgradeTo(1500,
            new Schemas.TableDowngrade("Deals",
                new Schemas.DropTable()))
        .build();

    db.getSchema(1000).getCreateTableStatement("Deals");
  }

  @Test(expected = Throwable.class)
  public void shouldRejectDroppingNonExistingColumn() throws Exception {
    Schemas db = Schemas.Builder
        .currentSchema(2900,
            new Schemas.TableDefinition("Deals",
                new Schemas.AddColumn("id", "")))
        .downgradeTo(1500,
            new Schemas.TableDowngrade("Deals",
                new Schemas.DropColumn("wat?")))
        .build();

    db.getSchema(1000).getCreateTableStatement("Deals");
  }

  @Test(expected = Throwable.class)
  public void shouldRejectDroppingNonExistingConstraint() throws Exception {
    Schemas db = Schemas.Builder
        .currentSchema(2900,
            new Schemas.TableDefinition("Deals",
                new Schemas.AddConstraint("X")))
        .downgradeTo(1500,
            new Schemas.TableDowngrade("Deals",
                new Schemas.DropConstraint("wat?")))
        .build();

    db.getSchema(1000).getCreateTableStatement("Deals");
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectToBuildTableWithoutAnyColumns() throws Exception {
    Schemas.Builder
        .currentSchema(2900, new Schemas.TableDefinition("Deals"))
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectUpgradeOnlyWithAutoMigration() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .upgradeTo(1500, auto())
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectMultipleAutoMigrationsInSingleUpgrade() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .upgradeTo(1500, auto(), auto())
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectEmptyUpgrade() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .upgradeTo(1500)
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldRejectUpgradeWithNullMigrationsArray() throws Exception {
    Schemas.Builder
        .currentSchema(2900)
        .upgradeTo(1500, (Migration[]) null)
        .build();
  }
}
