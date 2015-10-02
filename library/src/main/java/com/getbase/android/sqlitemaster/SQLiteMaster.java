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

package com.getbase.android.sqlitemaster;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;

import java.util.ArrayList;
import java.util.List;

public final class SQLiteMaster {
  private static final CursorFactory CURSOR_FACTORY = new CursorFactory() {
    @SuppressWarnings("deprecation")
    @Override
    public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable, SQLiteQuery query) {
      return new SQLiteCursor(db, masterQuery, editTable, query);
    }
  };

  private SQLiteMaster() {
  }

  private interface SqliteMasterColumns {
    String NAME = "name";
    String SQL = "sql";
    String TYPE = "type";
  }

  private static final String SQLITE_MASTER_TABLE = "sqlite_master";

  public static List<SQLiteSchemaPart> getSQLiteSchemaParts(SQLiteDatabase db, SQLiteSchemaPartType partType) {
    return getSQLiteSchemaParts(
        db.queryWithFactory(
            CURSOR_FACTORY,
            false,
            SQLITE_MASTER_TABLE,
            null,
            SqliteMasterColumns.TYPE + "= ?",
            new String[] { partType.getTypeName() },
            null,
            null,
            SqliteMasterColumns.NAME,
            null
        )
    );
  }

  public static List<SQLiteSchemaPart> getSQLiteSchemaParts(SQLiteDatabase db) {
    return getSQLiteSchemaParts(
        db.queryWithFactory(
            CURSOR_FACTORY,
            false,
            SQLITE_MASTER_TABLE,
            null,
            null,
            null,
            null,
            null,
            SqliteMasterColumns.NAME,
            null
        )
    );
  }

  private static List<SQLiteSchemaPart> getSQLiteSchemaParts(Cursor c) {
    List<SQLiteSchemaPart> result = new ArrayList<>();
    if (c != null) {
      try {
        if (c.moveToFirst()) {
          do {
            result.add(new SQLiteSchemaPart(
                c.getString(c.getColumnIndexOrThrow(SqliteMasterColumns.NAME)),
                c.getString(c.getColumnIndexOrThrow(SqliteMasterColumns.SQL)),
                c.getString(c.getColumnIndexOrThrow(SqliteMasterColumns.TYPE))
            ));
          } while (c.moveToNext());
        }
      } finally {
        c.close();
      }
    }
    return result;
  }

  public static void dropTriggers(SQLiteDatabase db) {
    for (SQLiteSchemaPart trigger : getSQLiteSchemaParts(db, SQLiteSchemaPartType.TRIGGER)) {
      db.execSQL("DROP TRIGGER IF EXISTS " + trigger.name);
    }
  }

  public static void dropIndexes(SQLiteDatabase db) {
    for (SQLiteSchemaPart index : getSQLiteSchemaParts(db, SQLiteSchemaPartType.INDEX)) {
      if (!index.name.startsWith("sqlite_")) {
        db.execSQL("DROP INDEX IF EXISTS " + index.name);
      }
    }
  }

  public static void dropViews(SQLiteDatabase db) {
    for (SQLiteSchemaPart view : getSQLiteSchemaParts(db, SQLiteSchemaPartType.VIEW)) {
      db.execSQL("DROP VIEW IF EXISTS " + view.name);
    }
  }
}
