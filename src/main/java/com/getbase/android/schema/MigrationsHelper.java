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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MigrationsHelper {
  private static final String TAG = MigrationsHelper.class.getSimpleName();

  private static int tempTableIndex = 0;

  public void performMigrations(SQLiteDatabase db, TableMigration... migrations) {
    for (TableMigration migration : migrations) {
      final String tempTable = "tmp_" + tempTableIndex++;
      db.execSQL("ALTER TABLE " + migration.tableName + " RENAME TO " + tempTable);
      ImmutableSet<String> oldColumns = getColumns(db, tempTable);

      db.execSQL(migration.createTableStatement);
      final String tempNewTable = "tmp_" + tempTableIndex++;
      db.execSQL("ALTER TABLE " + migration.tableName + " RENAME TO " + tempNewTable);
      ImmutableSet<String> newColumns = getColumns(db, tempNewTable);

      db.execSQL("ALTER TABLE " + tempNewTable + " RENAME TO " + migration.tableName);

      Set<String> commonColumns = Sets.intersection(oldColumns, newColumns);
      Set<String> droppedColumns = Sets.difference(oldColumns, newColumns);
      if (!droppedColumns.isEmpty()) {
        Log.w(TAG, "Dropping columns " + Joiner.on(",").join(droppedColumns) + " during migration of " + migration.tableName);
      }

      Set<String> addedColumns = Sets.difference(Sets.difference(newColumns, oldColumns), migration.mappings.keySet());
      if (!addedColumns.isEmpty()) {
        Log.w(TAG, "Will try to add new columns " + Joiner.on(",").join(addedColumns) + " during migration of " + migration.tableName);
      }

      SetView<String> unmappedColumns = Sets.difference(commonColumns, migration.mappings.keySet());
      String insertColumnsString = Joiner.on(",").join(Iterables.concat(unmappedColumns, migration.mappings.keySet()));
      String selectColumnsString = Joiner.on(",").join(Iterables.concat(unmappedColumns, migration.mappings.values()));

      db.execSQL("INSERT INTO " + migration.tableName + "(" + insertColumnsString + ") SELECT " + selectColumnsString + " FROM " + tempTable);
      db.execSQL("DROP TABLE " + tempTable);
    }
  }

  public static class TableMigration {
    private final String tableName;
    private final String createTableStatement;
    private final ImmutableMap<String, String> mappings;

    private TableMigration(String tableName, String createTableStatement, ImmutableMap<String, String> mappings) {
      this.tableName = tableName;
      this.createTableStatement = createTableStatement;
      this.mappings = mappings;
    }

    public static Builder of(String table) {
      return new Builder(table);
    }

    public static class Builder {
      private Map<String, String> mMappings = new HashMap<String, String>();
      private String mCreateTableStatement;
      private String mTable;

      Builder(String table) {
        mTable = checkNotNull(table);
      }

      public Builder to(String createTableStatement) {
        mCreateTableStatement = checkNotNull(createTableStatement);
        return this;
      }

      public Builder withMapping(String newColumn, String oldColumnExpression) {
        mMappings.put(checkNotNull(newColumn), checkNotNull(oldColumnExpression));
        return this;
      }

      public TableMigration build() {
        checkState(mTable != null);
        checkState(mCreateTableStatement != null);

        return new TableMigration(mTable, mCreateTableStatement, ImmutableMap.copyOf(mMappings));
      }
    }
  }

  static ImmutableSet<String> getColumns(SQLiteDatabase db, String table) {
    Cursor cursor = db.query(table, null, null, null, null, null, null, "0");
    if (cursor != null) {
      try {
        return ImmutableSet.copyOf(cursor.getColumnNames());
      } finally {
        cursor.close();
      }
    }
    return ImmutableSet.of();
  }
}
