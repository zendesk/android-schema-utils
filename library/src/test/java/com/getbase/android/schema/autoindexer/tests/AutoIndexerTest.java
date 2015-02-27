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

package com.getbase.android.schema.autoindexer.tests;

import com.getbase.android.schema.autoindexer.AutoIndexer;
import com.getbase.android.schema.autoindexer.SqliteIndex;

import org.chalup.thneed.ModelGraph;
import org.chalup.thneed.models.DatabaseModel;
import org.fest.assertions.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AutoIndexerTest {

  private static class SimpleDatabaseModel implements DatabaseModel {

    private final String mDbTable;

    private SimpleDatabaseModel(String dbTable) {
      mDbTable = dbTable;
    }

    @Override
    public String getTableName() {
      return mDbTable;
    }
  }

  private static SimpleDatabaseModel CONTACT = new SimpleDatabaseModel("contacts");
  private static SimpleDatabaseModel DEAL = new SimpleDatabaseModel("deals");
  private static SimpleDatabaseModel USER = new SimpleDatabaseModel("users");

  @Test
  public void shouldGenerateOnlyOneIndexIfThereAreMultipleReferences() throws Exception {
    ModelGraph<DatabaseModel> modelGraph = ModelGraph.of(DatabaseModel.class)
        .identifiedByDefault().by("id")
        .where()
        .the(DEAL).references(USER).by("user_id")
        .the(CONTACT).references(USER).by("user_id")
        .build();

    Set<SqliteIndex> indexes = AutoIndexer.generateIndexes(modelGraph);
    Assertions.assertThat(indexes).contains(
        new SqliteIndex("users", "id"),
        new SqliteIndex("deals", "user_id"),
        new SqliteIndex("contacts", "user_id")
    );
  }

  @Test
  public void shouldGenerateOnlyOneIndexIfThereAreMultipleReferencesToExplicitColumn() throws Exception {
    ModelGraph<DatabaseModel> modelGraph = ModelGraph.of(DatabaseModel.class)
        .identifiedByDefault().by("_id")
        .where()
        .the(DEAL).references("id").in(USER).by("user_id")
        .the(CONTACT).references("id").in(USER).by("user_id")
        .build();

    Set<SqliteIndex> indexes = AutoIndexer.generateIndexes(modelGraph);
    Assertions.assertThat(indexes).contains(
        new SqliteIndex("users", "id"),
        new SqliteIndex("deals", "user_id"),
        new SqliteIndex("contacts", "user_id")
    );
  }
}
