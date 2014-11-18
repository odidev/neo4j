/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.storemigration.legacystore.v21.propertydeduplication;

import org.neo4j.collection.primitive.PrimitiveLongObjectVisitor;
import org.neo4j.collection.primitive.PrimitiveLongVisitor;
import org.neo4j.kernel.impl.core.Token;
import org.neo4j.kernel.impl.store.PropertyKeyTokenStore;
import org.neo4j.kernel.impl.store.PropertyStore;
import org.neo4j.kernel.impl.store.record.PropertyBlock;
import org.neo4j.kernel.impl.store.record.PropertyKeyTokenRecord;
import org.neo4j.kernel.impl.store.record.PropertyRecord;
import org.neo4j.kernel.impl.transaction.state.Loaders;
import org.neo4j.kernel.impl.transaction.state.TokenCreator;
import org.neo4j.unsafe.batchinsert.DirectRecordAccess;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class NonIndexedConflictResolver implements PrimitiveLongObjectVisitor<List<DuplicateCluster>>
{
    private final PropertyKeyTokenStore keyTokenStore;
    private final Map<String, Integer> propertyTokenMap;
    private final PropertyStore store;

    public NonIndexedConflictResolver( PropertyKeyTokenStore keyTokenStore,
                                       PropertyStore store ) throws IOException
    {
        this.keyTokenStore = keyTokenStore;
        this.propertyTokenMap = buildPropertyKeyIndex( keyTokenStore );;
        this.store = store;
    }

    private Map<String,Integer> buildPropertyKeyIndex( PropertyKeyTokenStore tokenStore ) throws IOException
    {
        Token[] tokens = tokenStore.getTokens( (int) tokenStore.getHighestPossibleIdInUse() + 1 );
        Map<String,Integer> map = new HashMap<>();
        for ( Token token : tokens )
        {
            map.put( token.name(), token.id() );
        }
        return map;
    }

    @Override
    public void visited( long key, List<DuplicateCluster> duplicateClusters)
    {
        for ( DuplicateCluster duplicateCluster : duplicateClusters)
        {
            resolveConflict(duplicateCluster);
        }
    }

    private void resolveConflict( final DuplicateCluster duplicateCluster) {
        final String oldName = keyTokenStore.getToken( duplicateCluster.propertyKeyId ).name();
        DuplicateNameAssigner visitor = new DuplicateNameAssigner(duplicateCluster, oldName);
        duplicateCluster.propertyRecordIds.visitKeys(visitor);
    }

    private int getOrCreatePropertyKeyToken( String name, PropertyKeyTokenStore keyTokenStore ) throws IOException
    {
        Integer token = propertyTokenMap.get( name );
        if ( token != null )
        {
            return token;
        }
        TokenCreator<PropertyKeyTokenRecord> creator = new TokenCreator<>( keyTokenStore );
        DirectRecordAccess<Integer,PropertyKeyTokenRecord,Void> recordAccess = new DirectRecordAccess<>(
                keyTokenStore, Loaders.propertyKeyTokenLoader( keyTokenStore )
        );
        int propertyKeyTokenId = (int) keyTokenStore.nextId();
        creator.createToken( name, propertyKeyTokenId, recordAccess );
        recordAccess.close();
        propertyTokenMap.put( name, propertyKeyTokenId );
        return propertyKeyTokenId;
    }

    private class DuplicateNameAssigner implements PrimitiveLongVisitor {
        private final DuplicateCluster duplicateCluster;
        private final String oldName;
        int index;

        public DuplicateNameAssigner(DuplicateCluster duplicateCluster, String oldName) {
            this.duplicateCluster = duplicateCluster;
            this.oldName = oldName;
            index = 0;
        }

        @Override
        public void visited(long propertyRecordId) {
            PropertyRecord record = store.getRecord( propertyRecordId );
            try {
                for ( PropertyBlock block: record.getPropertyBlocks() )
                {
                    if (block.getKeyIndexId() == duplicateCluster.propertyKeyId)
                    {
                        if (index == 0) {
                            index += 1;
                        } else {
                            block.setKeyIndexId(getNewPropertyKeyId());
                        }
                    }
                }
            } catch (IOException e) {
                throw new InnerIterationIOException( e );
            }
            store.updateRecord( record );
        }

        int getNewPropertyKeyId() throws IOException
        {
            String duplicateName = "__DUPLICATE_" + oldName + "_" + index;
            index++;
            return getOrCreatePropertyKeyToken( duplicateName, keyTokenStore );
        }
    }
}
