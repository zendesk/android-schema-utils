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
import android.database.sqlite.SQLiteOpenHelper;

public class TestDatabase extends SQLiteOpenHelper {

  public static final String TEST_DB_NAME = "test.db";

  private final Context mContext;
  private final Schemas mSchemas;
  private final int mVersion;

  public TestDatabase(Context context, Schemas schemas, int version) {
    super(context, TEST_DB_NAME, null, version);

    mContext = context;
    mSchemas = schemas;
    mVersion = version;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    Schema schema = mSchemas.getSchema(mVersion);
    for (String table : schema.getTables()) {
      db.execSQL(schema.getCreateTableStatement(table));
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    mSchemas.upgrade(mContext, db, oldVersion, newVersion);
  }
}
