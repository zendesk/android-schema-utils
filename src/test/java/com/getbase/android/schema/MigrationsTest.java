/*
 * Copyright (C) 2013 Jerzy Chalupski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.getbase.android.schema;

import static org.fest.assertions.Assertions.assertThat;

import com.getbase.android.schema.Schemas.AddColumn;
import com.getbase.android.schema.Schemas.Builder;
import com.getbase.android.schema.Schemas.DropColumn;
import com.getbase.android.schema.Schemas.TableDefinition;
import com.getbase.android.schema.Schemas.TableDowngrade;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.database.sqlite.SQLiteDatabase;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MigrationsTest {

  private static final MigrationsHelper MIGRATIONS_HELPER = new MigrationsHelper();

  private static final Schemas SCHEMAS = Builder
      .currentSchema(4,
          new TableDefinition("Contacts",
              new AddColumn("id", "INTEGER"),
              new AddColumn("created_at", "INTEGER"),
              new AddColumn("updated_at", "INTEGER")
          )
      )
      .upgradeTo(4,
          SimpleTableMigration
              .of("Contacts")
              .withMapping("created_at", "STRFTIME('%s', 'now')")
              .withMapping("updated_at", "STRFTIME('%s', 'now')")
              .using(MIGRATIONS_HELPER)
      )
      .downgradeTo(2,
          new TableDowngrade("Contacts", new DropColumn("updated_at"))
      )
      .downgradeTo(1,
          new TableDowngrade("Contacts", new DropColumn("created_at"))
      )
      .build();

  @Before
  public void setUp() throws Exception {
    Robolectric.application.deleteDatabase(TestDatabase.TEST_DB_NAME);
  }

  @Test
  public void shouldPerformSimpleAutoMigration() throws Exception {
    SQLiteDatabase v1 = getDb(SCHEMAS, 1);
    assertThat(MigrationsHelper.getColumns(v1, "Contacts")).containsOnly("id");
    v1.close();

    SQLiteDatabase v2 = getDb(SCHEMAS, 2);
    assertThat(MigrationsHelper.getColumns(v2, "Contacts")).containsOnly("id", "created_at");
    v2.close();
  }

  @Test
  public void shouldPerformSimpleMigrationWithDuplicatedMappingValue() throws Exception {
    getDb(SCHEMAS, 3).close();
    getDb(SCHEMAS, 4).close();
  }

  private SQLiteDatabase getDb(Schemas schemas, int version) {
    return new TestDatabase(Robolectric.application, schemas, version).getReadableDatabase();
  }
}
