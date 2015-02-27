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
import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

import org.chalup.thneed.ManyToManyRelationship;
import org.chalup.thneed.ModelGraph;
import org.chalup.thneed.OneToManyRelationship;
import org.chalup.thneed.OneToOneRelationship;
import org.chalup.thneed.PolymorphicRelationship;
import org.chalup.thneed.RecursiveModelRelationship;
import org.chalup.thneed.RelationshipVisitor;
import org.chalup.thneed.models.DatabaseModel;

import java.util.Set;

public final class AutoIndexer {

  public static final String AUTO_INDEX_PREFIX = "auto_index";

  private AutoIndexer() {
  }

  private static final Joiner COLUMN_JOINER = Joiner.on(",");

  public static String getCreateStatement(SQLiteIndex index) {
    return "CREATE INDEX " + index.getName() + " ON " + index.mTable + "(" + COLUMN_JOINER.join(index.mColumns) + ")";
  }

  public static Predicate<SQLiteIndex> isIndexOnColumn(final String columnName) {
    return new Predicate<SQLiteIndex>() {
      @Override
      public boolean apply(SQLiteIndex index) {
        return index.mColumns.length == 1 && index.mColumns[0].equalsIgnoreCase(columnName);
      }
    };
  }

  public static Set<SQLiteIndex> generateIndexes(ModelGraph<? extends DatabaseModel> modelGraph) {
    final Set<SQLiteIndex> indexes = Sets.newHashSet();

    modelGraph.accept(new RelationshipVisitor<DatabaseModel>() {
      @Override
      public void visit(OneToManyRelationship<? extends DatabaseModel> relationship) {
        DatabaseModel model = relationship.mModel;
        DatabaseModel referencedModel = relationship.mReferencedModel;

        indexes.add(new SQLiteIndex(model.getTableName(), relationship.mLinkedByColumn));
        indexes.add(new SQLiteIndex(referencedModel.getTableName(), relationship.mReferencedModelIdColumn));
      }

      @Override
      public void visit(OneToOneRelationship<? extends DatabaseModel> relationship) {
        DatabaseModel model = relationship.mModel;
        DatabaseModel linkedModel = relationship.mLinkedModel;

        indexes.add(new SQLiteIndex(linkedModel.getTableName(), relationship.mLinkedByColumn));
        indexes.add(new SQLiteIndex(model.getTableName(), relationship.mParentModelIdColumn));
      }

      @Override
      public void visit(RecursiveModelRelationship<? extends DatabaseModel> relationship) {
        DatabaseModel model = relationship.mModel;

        indexes.add(new SQLiteIndex(model.getTableName(), relationship.mModelIdColumn));
        indexes.add(new SQLiteIndex(model.getTableName(), relationship.mGroupByColumn));
      }

      @Override
      public void visit(ManyToManyRelationship<? extends DatabaseModel> relationship) {
        // no implementation necessary
      }

      @Override
      public void visit(PolymorphicRelationship<? extends DatabaseModel> relationship) {
        DatabaseModel model = relationship.mModel;
        indexes.add(new SQLiteIndex(model.getTableName(), relationship.mTypeColumnName, relationship.mIdColumnName));

        for (DatabaseModel DatabaseModel : relationship.mPolymorphicModels.values()) {
          indexes.add(new SQLiteIndex(DatabaseModel.getTableName(), relationship.mPolymorphicModelIdColumn));
        }
      }
    });

    return indexes;
  }
}
