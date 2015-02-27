package com.getbase.android.schema;

import static org.hamcrest.CoreMatchers.instanceOf;

import com.getbase.android.schema.Schemas.AddColumn;
import com.getbase.android.schema.Schemas.Release;
import com.getbase.android.schema.Schemas.TableDowngrade;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

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

  static Matcher<Throwable> is(Class<? extends Throwable> exceptionClass) {
    final Matcher<Object> objectMatcher = instanceOf(exceptionClass);
    return new BaseMatcher<Throwable>() {
      @Override
      public boolean matches(Object item) {
        return objectMatcher.matches(item);
      }

      @Override
      public void describeTo(Description description) {
        objectMatcher.describeTo(description);
      }
    };
  }
}
