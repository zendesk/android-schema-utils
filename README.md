Android schema utils
====================
Android library for simplifying database schema and migrations management.

Basic usage
-----------
The library provides the fluent API, which allows you to define current schema:
```java
@SuppressWarnings("deprecation")
private static final Schemas SCHEMA = Schemas.Builder
    .currentSchema(10,
        new TableDefinition(Tables.ADDRESSES, ImmutableList.<TableDefinitionOperation>builder()
            .addAll(getCommonColumns())
            .add(
                new AddColumn(Addresses.REGION, "TEXT"),
                new AddColumn(Addresses.ZIP, "TEXT"),
                new AddColumn(Addresses.COUNTRY, "TEXT"),
                new AddColumn(Addresses.CITY, "TEXT"),
                new AddColumn(Addresses.STREET, "TEXT ")
            )
            .build()
        ),
        // more tables
    )
```

Migrations that have to be performed:
```java
    .upgradeTo(7, recreate(Tables.STATS))
```

And downgrades that you need to apply to get the previous db schema:
```java
    .downgradeTo(4,
        new TableDowngrade(Tables.ADDRESSES,
            new DropColumn(Addresses.REGION)
        )
    )
    .downgradeTo(2, dropTable(Tables.ADDRESSES))
    .build();
```

This might look like a tedious, unnecessary work. In reality it is tedious, but very helpful work. It reduces the usual `db.execSQL()` boilerplate in `onCreate` and `onUpgrade` to this:
```java
@Override
public void onCreate(SQLiteDatabase db) {
  Schema currentSchema = SCHEMA.getCurrentSchema();
  for (String table : currentSchema.getTables()) {
    db.execSQL(currentSchema.getCreateTableStatement(table));
  }
}

@Override
public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
  SCHEMA.upgrade(oldVersion, mContext, db);
}
```

### Bulletproof workaround for SQLite's ALTER TABLE deficiencies
SQLite's ALTER TABLE supports only renaming the table or adding a new column. If you want to do anything more fancy like dropping the column, you have to do the following things:

* Rename table with old schema to some temporary name
* Create table with new schema
* Move the data from renamed table to new table
* Drop the old table

This library provides tested wrapper for this boilerplate with nice API:

```java
TableMigration migration = TableMigration
    .of(Tables.MY_TABLE)
    .withMapping(MyTable.SOME_COLUMN, "NULL")
    .to(CREATE_MY_TABLE)
    .build();
    
migrationHelper.performMigrations(db, migration);
```

**Warning**: the MigrationHelper is not thread-safe (but seriously, why on earth would you want to perform sequential schema migrations in parallel?).

### Write only non-trivial migrations
Most of the migrations you perform are trivial: dropping column, adding nullable column, adding table, dropping table, etc. If you specify complete schema history (which you should do anyways), this library will figure out trivial migrations for you. Of course you can still define fully custom migrations or just combine your custom migrations with our automagic:

```java
.upgradeTo(2,
    auto(),
    new SimpleMigration() {
      @Override
      public void apply(SQLiteDatabase db, Schema schema) {
        // usual db.execSQL() 
      }
    }
)
```

### Reduce merge conflicts
In your `Schemas` definition you can include `release` checkpoints. All revision numbers before this checkpoint are in fact offsets from this revision. It helps a lot when you are merging two branches, which introduced changes to your schema.

You still have to merge the section of code with `currentSchema` and you still have to make sure that both branches haven't performed the same changes, but you don't have to juggle the revision numbers and in 90% of cases you just need to decide which batch of changes should go first.

Building
--------
This is standard maven project. To build it just execute:
```shell
mvn clean package
```
in directory with pom.xml.

Todo
----
* Documentation
* Unit tests

License
-------
    Copyright (C) 2013 Jerzy Chalupski

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License. 
