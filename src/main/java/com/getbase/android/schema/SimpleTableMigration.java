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

import com.getbase.android.schema.MigrationsHelper.TableMigration;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

public class SimpleTableMigration implements Migration {
  private final String mTableName;
  private final TableMigration.Builder mMigrationBuilder;
  private final MigrationsHelper mMigrationsHelper;

  private SimpleTableMigration(String tableName, TableMigration.Builder migrationBuilder, MigrationsHelper migrationsHelper) {
    mTableName = tableName;
    mMigrationBuilder = migrationBuilder;
    mMigrationsHelper = migrationsHelper;
  }

  @Override
  public void apply(int version, SQLiteDatabase database, Schemas schemas, Context context) {
    mMigrationsHelper.performMigrations(database,
        mMigrationBuilder
            .to(schemas.getSchema(version).getCreateTableStatement(mTableName))
            .build()
    );
  }

  public static Builder of(String tableName) {
    return new Builder(tableName);
  }

  public static class Builder {
    private final TableMigration.Builder mMigrationBuilder;
    private final String mTableName;

    private Builder(String tableName) {
      mTableName = tableName;
      mMigrationBuilder = TableMigration.of(tableName);
    }

    public Builder withMapping(String newColumn, String oldColumnExpression) {
      mMigrationBuilder.withMapping(newColumn, oldColumnExpression);
      return this;
    }

    public SimpleTableMigration using(MigrationsHelper migrationsHelper) {
      return new SimpleTableMigration(mTableName, mMigrationBuilder, migrationsHelper);
    }
  }
}
