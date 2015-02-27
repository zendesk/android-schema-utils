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

import com.getbase.android.schema.Schemas.Schema;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.Arrays;

public final class Migrations {
  private Migrations() {
  }

  public static Migration auto() {
    return Schemas.AUTO_MIGRATION;
  }

  public static Migration drop(final String... tables) {
    return drop(Arrays.asList(tables));
  }

  public static Migration drop(final Iterable<String> tables) {
    return new SimpleMigration() {
      @Override
      public void apply(SQLiteDatabase db, Schema schema) {
        for (String table : tables) {
          db.execSQL("DROP TABLE IF EXISTS " + table);
        }
      }
    };
  }

  private static Migration create(final String... tables) {
    return create(Arrays.asList(tables));
  }

  public static Migration create(final Iterable<String> tables) {
    return new Migration() {

      @Override
      public void apply(int version, SQLiteDatabase database, Schemas schemas, Context context) {
        Schema schema = schemas.getSchema(version);

        for (String table : tables) {
          database.execSQL(schema.getCreateTableStatement(table));
        }
      }
    };
  }

  public static Migration recreate(final String... tables) {
    return new Migration() {

      @Override
      public void apply(int version, SQLiteDatabase database, Schemas schemas, Context context) {
        for (Migration migration : new Migration[] { drop(tables), create(tables) }) {
          migration.apply(version, database, schemas, context);
        }
      }
    };
  }
}
