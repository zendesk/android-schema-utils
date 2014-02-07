package com.getbase.android.schema.tests;

import com.getbase.android.schema.Schemas.Release;

public final class TestUtils {
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
