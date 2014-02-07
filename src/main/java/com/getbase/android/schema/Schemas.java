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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Schemas {
  private static final String TAG = Schemas.class.getSimpleName();

  private final ImmutableMap<Integer, Migration[]> mMigrations;
  private final ImmutableMap<Integer, ImmutableMap<String, ImmutableList<TableDowngradeOperation>>> mDowngrades;
  private final LoadingCache<Integer, ImmutableMap<String, ImmutableList<TableDefinitionOperation>>> mRevisions =
      CacheBuilder.newBuilder().build(
          new CacheLoader<Integer, ImmutableMap<String, ImmutableList<TableDefinitionOperation>>>() {
            @Override
            public ImmutableMap<String, ImmutableList<TableDefinitionOperation>> load(Integer key) throws Exception {
              Integer lastMergedRevision = Collections.min(mRevisions.asMap().keySet());
              Log.d(TAG, "Building migration to " + key + " (min schema prepared: " + lastMergedRevision + ")");
              Preconditions.checkState(lastMergedRevision > key, "Trying to retrieve version %s, which is higher than current schema version", key);
              ImmutableMap<String, ImmutableList<TableDefinitionOperation>> schema = mRevisions.getIfPresent(lastMergedRevision);
              Preconditions.checkState(schema != null);

              for (int revision = lastMergedRevision - 1; ; --revision) {
                Log.d(TAG, "Prepare schema for " + revision);
                if (mDowngrades.containsKey(revision)) {
                  schema = merge(schema, mDowngrades.get(revision), revision);
                }

                if (revision == key) {
                  return schema;
                } else {
                  mRevisions.put(revision, schema);
                }
              }
            }
          }
      );
  private final ImmutableList<Release> mReleases;

  private final Function<Integer, String> mRevisionDescriptionBuilder = new Function<Integer, String>() {
    @Override
    public String apply(final Integer revision) {
      Release release = Iterables.find(mReleases, new Predicate<Release>() {
        @Override
        public boolean apply(Release r) {
          return r.getSchemaVersion() <= revision;
        }
      });

      int downgradeOffset = revision - release.getSchemaVersion();
      return "revision " + revision + " (downgradeTo(" + downgradeOffset + ", ...) above " + release + ")";
    }
  };

  private ImmutableMap<String, ImmutableList<TableDefinitionOperation>> merge(
      ImmutableMap<String, ImmutableList<TableDefinitionOperation>> schema,
      ImmutableMap<String, ImmutableList<TableDowngradeOperation>> downgrades,
      int targetRevision) {
    ImmutableMap.Builder<String, ImmutableList<TableDefinitionOperation>> builder = ImmutableMap.builder();

    for (String unchangedTable : Sets.difference(schema.keySet(), downgrades.keySet())) {
      builder.put(unchangedTable, schema.get(unchangedTable));
    }

    for (String alteredTable : Sets.intersection(downgrades.keySet(), schema.keySet())) {
      ImmutableList<TableDefinitionOperation> mergedOperations = MERGER.merge(schema.get(alteredTable), downgrades.get(alteredTable), alteredTable, targetRevision, mRevisionDescriptionBuilder);
      if (!mergedOperations.isEmpty()) {
        builder.put(alteredTable, mergedOperations);
      }
    }

    for (String addedTable : Sets.difference(downgrades.keySet(), schema.keySet())) {
      builder.put(addedTable, CONVERTER.convert(downgrades.get(addedTable), addedTable, targetRevision, mRevisionDescriptionBuilder));
    }

    return builder.build();
  }

  private static final DowngradeToDefinitionConverter CONVERTER = new DowngradeToDefinitionConverter();

  private static class DowngradeToDefinitionConverter implements TableOperationVisitor {

    private ImmutableList.Builder<TableDefinitionOperation> builder;
    private String mTable;
    private int mTargetRevision;
    private Function<Integer, String> mRevisionDescriptionBuilder;

    public ImmutableList<TableDefinitionOperation> convert(ImmutableList<TableDowngradeOperation> downgrades, String table, int targetRevision, Function<Integer, String> revisionDescriptionBuilder) {
      mTable = table;
      mTargetRevision = targetRevision;
      mRevisionDescriptionBuilder = revisionDescriptionBuilder;
      builder = ImmutableList.builder();

      for (TableDowngradeOperation downgrade : downgrades) {
        downgrade.accept(this);
      }

      return builder.build();
    }

    @Override
    public void visit(AddColumn addColumn) {
      builder.add(addColumn);
    }

    @Override
    public void visit(DropColumn dropColumn) {
      throw new IllegalStateException();
    }

    @Override
    public void visit(DropTable dropTable) {
      throw new IllegalStateException("Trying to drop non existing table " + mTable + " while building " + mRevisionDescriptionBuilder.apply(mTargetRevision));
    }

    @Override
    public void visit(DropConstraint dropConstraint) {
      throw new IllegalStateException();
    }

    @Override
    public void visit(AddConstraint addConstraint) {
      builder.add(addConstraint);
    }
  }

  private static final TableOperationMerger MERGER = new TableOperationMerger();

  private static class TableOperationMerger implements TableOperationVisitor {
    private String mTable;
    private int mTargetRevision;
    private Function<Integer, String> mRevisionDescriptionBuilder;
    private Map<TableOperationId, TableDefinitionOperation> mMergedOperations;

    public ImmutableList<TableDefinitionOperation> merge(ImmutableList<TableDefinitionOperation> schema, ImmutableList<TableDowngradeOperation> downgrades, String table, int targetRevision, Function<Integer, String> revisionDescriptionBuilder) {
      mTable = table;
      mTargetRevision = targetRevision;
      mRevisionDescriptionBuilder = revisionDescriptionBuilder;
      mMergedOperations = Maps.newHashMap();

      for (TableOperation operation : schema) {
        operation.accept(this);
      }

      for (TableOperation operation : downgrades) {
        operation.accept(this);
      }

      return ImmutableList.copyOf(mMergedOperations.values());
    }

    @Override
    public void visit(AddColumn addColumn) {
      mMergedOperations.put(addColumn.getId(), addColumn);
    }

    @Override
    public void visit(DropColumn dropColumn) {
      TableDefinitionOperation droppedColumn = mMergedOperations.remove(dropColumn.getId());
      Preconditions.checkState(
          droppedColumn != null,
          "Trying to drop non existing column %s.%s while building %s",
          mTable, dropColumn.mColumnName, mRevisionDescriptionBuilder.apply(mTargetRevision)
      );
    }

    @Override
    public void visit(DropTable dropTable) {
      mMergedOperations.clear();
    }

    @Override
    public void visit(DropConstraint dropConstraint) {
      TableDefinitionOperation droppedConstraint = mMergedOperations.remove(dropConstraint.getId());
      Preconditions.checkState(
          droppedConstraint != null,
          "Trying to drop non existing constraint '%s' on table %s while building %s",
          dropConstraint.mConstraintDefinition, mTable, mRevisionDescriptionBuilder.apply(mTargetRevision)
      );
    }

    @Override
    public void visit(AddConstraint addConstraint) {
      mMergedOperations.put(addConstraint.getId(), addConstraint);
    }
  }

  private Schemas(int currentRevision,
      Map<String, ImmutableList<TableDefinitionOperation>> tables,
      ImmutableMap<Integer, ImmutableMap<String, ImmutableList<TableDowngradeOperation>>> downgrades,
      ImmutableMap<Integer, Migration[]> migrations,
      ImmutableList<Release> releases) {
    mRevisions.put(currentRevision, ImmutableMap.copyOf(tables));
    mDowngrades = downgrades;
    mMigrations = migrations;
    mReleases = releases;
  }

  public Schema getSchema(int version) {
    return new Schema(version);
  }

  public Schema getCurrentSchema() {
    return getSchema(getCurrentRevisionNumber());
  }

  public ImmutableSet<String> getTablesModifiedInRevision(int version) {
    return mDowngrades.containsKey(version - 1)
        ? mDowngrades.get(version - 1).keySet()
        : ImmutableSet.<String>of();
  }

  public class Schema {
    private final ImmutableMap<String, ImmutableList<TableDefinitionOperation>> mTableDefinitions;
    private final int mVersion;

    private Schema(int version) {
      mVersion = version;
      mTableDefinitions = mRevisions.getUnchecked(version);
    }

    public String getCreateTableStatement(String tableName) {
      Preconditions.checkArgument(mTableDefinitions.containsKey(tableName), "Schema for version %s doesn't contain table %s", mVersion, tableName);
      return new TableCreateStatementBuilder().build(tableName, mTableDefinitions.get(tableName));
    }

    public ImmutableSet<String> getTables() {
      return mTableDefinitions.keySet();
    }

    public ImmutableSet<String> getColumns(String table) {
      return COLUMNS_GETTER.getColumns(mTableDefinitions.get(table));
    }
  }

  private static final ColumnsGetter COLUMNS_GETTER = new ColumnsGetter();

  public static class ColumnsGetter implements TableOperationVisitor {

    ImmutableSet.Builder<String> mBuilder;

    public ImmutableSet<String> getColumns(Iterable<TableDefinitionOperation> operations) {
      mBuilder = ImmutableSet.builder();

      for (TableDefinitionOperation operation : operations) {
        operation.accept(this);
      }

      return mBuilder.build();
    }

    @Override
    public void visit(AddColumn addColumn) {
      mBuilder.add(addColumn.mColumnName);
    }

    @Override
    public void visit(DropColumn dropColumn) {
      throw new IllegalStateException();
    }

    @Override
    public void visit(DropTable dropTable) {
      throw new IllegalStateException();
    }

    @Override
    public void visit(DropConstraint dropConstraint) {
      throw new IllegalStateException();
    }

    @Override
    public void visit(AddConstraint addConstraint) {
      // ignore
    }
  }

  private final Supplier<Integer> mCurrentRevision = Suppliers.memoize(new Supplier<Integer>() {
    @Override
    public Integer get() {
      return Collections.max(mRevisions.asMap().keySet());
    }
  });

  public int getCurrentRevisionNumber() {
    return mCurrentRevision.get();
  }

  public static class TableCreateStatementBuilder implements TableOperationVisitor {
    private List<String> mColumns;
    private List<String> mConstraints;

    public String build(String tableName, ImmutableList<TableDefinitionOperation> operations) {
      mColumns = Lists.newArrayList();
      mConstraints = Lists.newArrayList();

      for (TableOperation operation : operations) {
        operation.accept(this);
      }

      List<String> parts = Lists.newArrayList();
      parts.addAll(mColumns);
      parts.addAll(mConstraints);

      return "CREATE TABLE " + tableName + "(" + Joiner.on(", ").join(parts) + ")";
    }

    @Override
    public void visit(AddColumn addColumn) {
      mColumns.add(addColumn.mColumnName + " " + addColumn.mColumnDefinition);
    }

    @Override
    public void visit(DropColumn dropColumn) {
      throw new IllegalStateException("Received DropColumn operation for building create statement");
    }

    @Override
    public void visit(DropTable dropTable) {
      throw new IllegalStateException("Received DropTable operation for building create statement");
    }

    @Override
    public void visit(DropConstraint dropConstraint) {
      throw new IllegalStateException("Received DropConstraint operation for building create statement");
    }

    @Override
    public void visit(AddConstraint addConstraint) {
      mConstraints.add(addConstraint.mConstraintDefinition);
    }
  }

  private enum OperationScope {
    Table,
    Column,
    TableConstraint
  }

  interface TableOperationVisitor {
    void visit(AddColumn addColumn);
    void visit(DropColumn dropColumn);
    void visit(DropTable dropTable);
    void visit(DropConstraint dropConstraint);
    void visit(AddConstraint addConstraint);
  }

  public interface TableOperation {
    TableOperationId getId();
    void accept(TableOperationVisitor visitor);
  }

  private static class TableOperationId {
    private final OperationScope mScope;
    private final String mName;

    public TableOperationId(OperationScope scope, String name) {
      mScope = Preconditions.checkNotNull(scope);
      mName = Preconditions.checkNotNull(name);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TableOperationId that = (TableOperationId) o;

      return Objects.equal(mScope, that.mScope) && Objects.equal(mName, that.mName);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(mScope, mName);
    }
  }

  public interface TableDowngradeOperation extends TableOperation {
  }

  public interface TableDefinitionOperation extends TableOperation {
  }

  public static class AddColumn implements TableDefinitionOperation, TableDowngradeOperation {
    private final String mColumnName;
    private final String mColumnDefinition;
    private final TableOperationId mId;

    public AddColumn(String columnName, String columnDefinition) {
      mColumnName = columnName;
      mColumnDefinition = columnDefinition;
      mId = new TableOperationId(OperationScope.Column, columnName);
    }

    @Override
    public TableOperationId getId() {
      return mId;
    }

    @Override
    public void accept(TableOperationVisitor visitor) {
      visitor.visit(this);
    }
  }

  public static class DropColumn implements TableDowngradeOperation {
    public final String mColumnName;
    private final TableOperationId mId;

    public DropColumn(String columnName) {
      mColumnName = columnName;
      mId = new TableOperationId(OperationScope.Column, columnName);
    }

    @Override
    public TableOperationId getId() {
      return mId;
    }

    @Override
    public void accept(TableOperationVisitor visitor) {
      visitor.visit(this);
    }
  }

  public static class DropTable implements TableDowngradeOperation {
    static final TableOperationId DROP_TABLE_OPERATION_ID = new TableOperationId(OperationScope.Table, "");

    @Override
    public TableOperationId getId() {
      return DROP_TABLE_OPERATION_ID;
    }

    @Override
    public void accept(TableOperationVisitor visitor) {
      visitor.visit(this);
    }
  }

  public static class AddConstraint implements TableDefinitionOperation, TableDowngradeOperation {
    private final String mConstraintDefinition;
    private final TableOperationId mId;

    public AddConstraint(String constraintDefinition) {
      mConstraintDefinition = constraintDefinition;
      mId = new TableOperationId(OperationScope.TableConstraint, constraintDefinition);
    }

    @Override
    public TableOperationId getId() {
      return mId;
    }

    @Override
    public void accept(TableOperationVisitor visitor) {
      visitor.visit(this);
    }
  }

  public static class DropConstraint implements TableDowngradeOperation {
    public final String mConstraintDefinition;
    private final TableOperationId mId;

    public DropConstraint(String constraintDefinition) {
      mConstraintDefinition = constraintDefinition;
      mId = new TableOperationId(OperationScope.TableConstraint, constraintDefinition);
    }

    @Override
    public TableOperationId getId() {
      return mId;
    }

    @Override
    public void accept(TableOperationVisitor visitor) {
      visitor.visit(this);
    }
  }

  public static class TableDefinition extends SchemaPart<TableDefinitionOperation> {
    public TableDefinition(String tableName, TableDefinitionOperation... operations) {
      super(tableName, operations);
    }

    public TableDefinition(String tableName, ImmutableList<TableDefinitionOperation> operations) {
      super(tableName, operations);
    }
  }

  public static TableDowngrade dropTable(String tableName) {
    return new TableDowngrade(tableName, new DropTable());
  }

  public static class TableDowngrade extends SchemaPart<TableDowngradeOperation> {
    public TableDowngrade(String tableName, TableDowngradeOperation... operations) {
      super(tableName, operations);
    }

    public TableDowngrade(String tableName, ImmutableList<TableDowngradeOperation> operations) {
      super(tableName, operations);
    }
  }

  private static class SchemaPart<T extends TableOperation> {
    protected final String mTableName;
    protected final ImmutableList<T> mOperations;

    private SchemaPart(String tableName, T... operations) {
      this(tableName, ImmutableList.copyOf(operations));
    }

    private SchemaPart(String tableName, ImmutableList<T> operations) {
      validateTableOperations(tableName, operations);

      mTableName = tableName;
      mOperations = operations;
    }
  }

  private static void validateTableOperations(String tableName, ImmutableList<? extends TableOperation> operations) {
    Preconditions.checkArgument(!operations.isEmpty(), "Schema part should contain at least one operation");

    List<TableOperationId> ids = Lists.newArrayList();
    for (TableOperation operation : operations) {
      ids.add(operation.getId());
    }

    if (ids.contains(DropTable.DROP_TABLE_OPERATION_ID)) {
      Preconditions.checkArgument(operations.size() == 1, "DropTable operation in downgrade definition for table " + tableName + " cannot be mixed with other operations");
    }
    Preconditions.checkArgument(Sets.newHashSet(ids).size() == ids.size(), "Duplicate operations on single column or constraint in " + tableName);
  }

  public static class Builder {
    private final int mCurrentRevisionOffset;
    private final Map<String, ImmutableList<TableDefinitionOperation>> mTables = Maps.newHashMap();
    private final ImmutableMap.Builder<Integer, ImmutableMap<String, ImmutableList<TableDowngradeOperation>>> mDowngradesBuilder = ImmutableMap.builder();
    private final ImmutableMap.Builder<Integer, Migration[]> mMigrationsBuilder = ImmutableMap.builder();
    private final ImmutableList.Builder<Release> mReleasesBuilder = ImmutableList.builder();

    private Integer mCurrentOffset;
    private boolean mUpgradeToCurrentOffsetDefined;

    private Release mLastRelease;

    private Release mCurrentRelease;
    private final Map<Integer, ImmutableMap<String, ImmutableList<TableDowngradeOperation>>> mPendingDowngrades = Maps.newHashMap();
    private final Map<Integer, Migration[]> mPendingMigrations = Maps.newHashMap();

    private Builder(int offset, TableDefinition[] tables) {
      for (TableDefinition table : tables) {
        Preconditions.checkArgument(mTables.put(table.mTableName, table.mOperations) == null, "Duplicate table " + table.mTableName + " in current schema");
      }

      mCurrentRevisionOffset = offset;
      mCurrentOffset = offset;
      mUpgradeToCurrentOffsetDefined = false;
    }

    public static OldSchemasBuilder currentSchema(int revision, TableDefinition... tables) {
      return new Builder(revision, tables).new OldSchemasBuilder();
    }

    public class OldSchemasBuilder {
      public OldSchemasBuilder downgradeTo(int offset, TableDowngrade... tableDowngrades) {
        Preconditions.checkArgument(offset >= 0, "Downgrade offset cannot be negative");
        Preconditions.checkArgument(
            mCurrentOffset == null || offset < mCurrentOffset,
            "Downgrades and upgrades definitions should have descending offsets. The downgrade offset (%s) should be lower than current offset (%s)",
            offset, mCurrentOffset
        );

        Map<String, ImmutableList<TableDowngradeOperation>> downgrades = Maps.newHashMap();
        for (TableDowngrade tableDowngrade : tableDowngrades) {
          Preconditions.checkArgument(
              downgrades.put(tableDowngrade.mTableName, tableDowngrade.mOperations) == null,
              "Duplicate tableDowngrade for table " + tableDowngrade.mTableName + " in downgrade to " + offset
          );
        }

        mPendingDowngrades.put(offset, ImmutableMap.copyOf(downgrades));
        mCurrentOffset = offset;
        mUpgradeToCurrentOffsetDefined = false;

        return this;
      }

      public OldSchemasBuilder upgradeTo(int offset, Migration... migrations) {
        Preconditions.checkArgument(offset > 0, "In upgradeTo(%s, ...): Upgrade offset should be greater than 0", offset);
        Preconditions.checkArgument(migrations != null, "In upgradeTo(%s, ...): migrations cannot be null", offset);
        Preconditions.checkArgument(migrations.length > 0, "In upgradeTo(%s, ...): migrations cannot be empty", offset);

        switch (migrations.length) {
        case 1:
          Preconditions.checkArgument(migrations[0] != AUTO_MIGRATION,
              "In upgradeTo(%s, ...): upgrades with a single auto() migration are implicitly performed for every revision without explicit upgradeTo()",
              offset
          );
          break;
        default:
          Preconditions.checkArgument(
              FluentIterable
                  .from(Arrays.asList(migrations))
                  .filter(Predicates.equalTo(AUTO_MIGRATION))
                  .size() <= 1,
              "In upgradeTo(%s, ...): only one auto() migration per upgrade is allowed",
              offset
          );
        }

        if (mCurrentOffset != null) {
          if (mUpgradeToCurrentOffsetDefined) {
            Preconditions.checkArgument(offset < mCurrentOffset,
                "Downgrades and upgrades definitions should have descending offsets. The upgrade to %s is already defined, so upgrade offset %s should be lower than %s",
                mCurrentOffset, offset, mCurrentOffset
            );
          } else {
            Preconditions.checkArgument(offset <= mCurrentOffset,
                "Downgrades and upgrades definitions should have descending offsets. The upgrade offset %s should be lower or equal to %s",
                offset, mCurrentOffset
            );
          }
        }

        mPendingMigrations.put(offset, migrations);
        mUpgradeToCurrentOffsetDefined = true;
        mCurrentOffset = offset;
        return this;
      }

      public OldSchemasBuilder release(Release release) {
        if (mLastRelease != null) {
          Preconditions.checkArgument(release.getSchemaVersion() <= mLastRelease.getSchemaVersion(),
              "Releases should have non-ascending revision numbers. The previous release had version number %s, so the release with revision number %s is not valid",
              mLastRelease.getSchemaVersion(), release.getSchemaVersion()
          );
        }

        if (mCurrentRelease == null) {
          mCurrentRelease = release;
        }
        processPendingSchemaParts(release);

        mCurrentOffset = null;
        mUpgradeToCurrentOffsetDefined = false;

        mLastRelease = release;

        return this;
      }

      public Schemas build() {
        processPendingSchemaParts(new Release() {
          @Override
          public int getSchemaVersion() {
            return 0;
          }

          @Override
          public String toString() {
            return "INITIAL DB SCHEMA";
          }
        });
        return Builder.this.build();
      }

      private void processPendingSchemaParts(Release release) {
        mReleasesBuilder.add(release);
        int baseRevisionNumber = release.getSchemaVersion();

        for (Integer downgradeOffset : mPendingDowngrades.keySet()) {
          int revisionNumber = downgradeOffset + baseRevisionNumber;
          if (mLastRelease != null) {
            Preconditions.checkArgument(revisionNumber < mLastRelease.getSchemaVersion(),
                "The downgrade with offset %s defined between release %s with version %s and release %s with version %s is outside of valid range [0, %s).",
                downgradeOffset, release, release.getSchemaVersion(), mLastRelease, mLastRelease.getSchemaVersion(), (mLastRelease.getSchemaVersion() - release.getSchemaVersion())
            );
          }
          mDowngradesBuilder.put(revisionNumber, mPendingDowngrades.get(downgradeOffset));
        }
        mPendingDowngrades.clear();

        for (Integer migrationOffset : mPendingMigrations.keySet()) {
          int revisionNumber = migrationOffset + baseRevisionNumber;
          if (mLastRelease != null) {
            Preconditions.checkArgument(revisionNumber <= mLastRelease.getSchemaVersion(),
                "The upgrade with offset %s defined between release %s with version %s and release %s with version %s is outside of valid range (0, %s].",
                migrationOffset, release, release.getSchemaVersion(), mLastRelease, mLastRelease.getSchemaVersion(), (mLastRelease.getSchemaVersion() - release.getSchemaVersion())
            );
          }
          mMigrationsBuilder.put(migrationOffset + baseRevisionNumber, mPendingMigrations.get(migrationOffset));
        }
        mPendingMigrations.clear();
      }
    }

    private Schemas build() {
      return new Schemas(
          mCurrentRelease != null
              ? mCurrentRelease.getSchemaVersion() + mCurrentRevisionOffset
              : mCurrentRevisionOffset,
          mTables,
          mDowngradesBuilder.build(),
          mMigrationsBuilder.build(),
          mReleasesBuilder.build());
    }
  }

  public interface Release {
    int getSchemaVersion();
  }

  static final MigrationsHelper AUTO_MIGRATION_HELPER = new MigrationsHelper();
  static final Migration AUTO_MIGRATION = new Migration() {
    @Override
    public void apply(int version, SQLiteDatabase database, Schemas schemas, Context context) {
      ImmutableSet<String> modifiedTables = schemas.getTablesModifiedInRevision(version);
      if (!modifiedTables.isEmpty()) {
        ImmutableSet<String> newTables = schemas.getSchema(version).getTables();
        ImmutableSet<String> oldTables = schemas.getSchema(version - 1).getTables();

        SetView<String> addedTables = Sets.difference(newTables, oldTables);
        Migrations.create(addedTables).apply(version, database, schemas, context);

        SetView<String> droppedTables = Sets.difference(oldTables, newTables);
        Migrations.drop(droppedTables).apply(version, database, schemas, context);

        SetView<String> commonTables = Sets.intersection(oldTables, newTables);
        SetView<String> alteredTables = Sets.intersection(commonTables, modifiedTables);

        for (String table : alteredTables) {
          SimpleTableMigration.of(table).using(AUTO_MIGRATION_HELPER).apply(version, database, schemas, context);
        }
      }
    }
  };

  private static final Migration[] AUTO_MIGRATIONS = new Migration[] { AUTO_MIGRATION };

  Migration[] to(int revision) {
    return Objects.firstNonNull(
        mMigrations.get(revision),
        AUTO_MIGRATIONS
    );
  }

  public void upgrade(int fromVersion, Context context, SQLiteDatabase database) {
    int currentVersion = getCurrentRevisionNumber();
    for (int version = fromVersion + 1; version <= currentVersion; version++) {
      Log.d(TAG, "Perform migration to " + version);
      for (Migration migration : to(version)) {
        migration.apply(version, database, this, context);
      }
    }
  }
}
