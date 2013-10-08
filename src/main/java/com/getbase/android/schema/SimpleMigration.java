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

public abstract class SimpleMigration implements Migration {

  @Override
  public void apply(int version, SQLiteDatabase database, Schemas schemas, Context context) {
    apply(database, schemas.getSchema(version));
  }

  public abstract void apply(SQLiteDatabase db, Schema schema);
}
