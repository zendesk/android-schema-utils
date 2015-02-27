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

package com.getbase.android.schema.autoindexer;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;

import java.util.Arrays;

public class SQLiteIndex {
  public final String mTable;
  public final String[] mColumns;

  private static final Joiner INDEX_NAME_JOINER = Joiner.on("_");

  public SQLiteIndex(String table, String... columns) {
    mTable = table;
    mColumns = columns;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SQLiteIndex that = (SQLiteIndex) o;

    return Objects.equal(mTable, that.mTable) &&
        Arrays.equals(mColumns, that.mColumns);
  }

  public String getName() {
    return INDEX_NAME_JOINER.join(AutoIndexer.AUTO_INDEX_PREFIX, mTable, mColumns);
  }

  @Override
  public String toString() {
    return "SQLiteIndex on " + mTable + Arrays.toString(mColumns);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mTable, Arrays.hashCode(mColumns));
  }
}
