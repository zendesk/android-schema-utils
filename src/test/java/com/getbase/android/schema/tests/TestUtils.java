package com.getbase.android.schema.tests;

import com.getbase.android.schema.Migration;
import com.getbase.android.schema.Schemas;
import com.getbase.android.schema.Schemas.AddColumn;
import com.getbase.android.schema.Schemas.Release;
import com.getbase.android.schema.Schemas.TableDowngrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

public final class TestUtils {
  public static final Migration EMPTY_MIGRATION = new Migration() {
    @Override
    public void apply(int version, SQLiteDatabase database, Schemas schemas, Context context) {

    }
  };

  public static final TableDowngrade VALID_DOWNGRADE = new TableDowngrade("Deals",
      new AddColumn("ID", "")
  );

  private TestUtils() {
  }

  static Release release(final int revision) {
    return new Release() {
      @Override
      public int getSchemaVersion() {
        return revision;
      }

      @Override
      public String toString() {
        return "Test Release [" + revision + "]";
      }
    };
  }
}
