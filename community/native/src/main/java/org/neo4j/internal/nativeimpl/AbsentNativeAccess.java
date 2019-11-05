/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
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
package org.neo4j.internal.nativeimpl;

public class AbsentNativeAccess implements NativeAccess
{
    @Override
    public boolean isAvailable()
    {
        return false;
    }

    @Override
    public int tryEvictFromCache( int fd )
    {
        return SUCCESS;
    }

    @Override
    public int tryAdviseSequentialAccess( int fd )
    {
        return SUCCESS;
    }

    @Override
    public int tryAdviseToKeepInCache( int fd )
    {
        return SUCCESS;
    }

    @Override
    public int tryPreallocateSpace( int fd, long bytes )
    {
        return SUCCESS;
    }

    @Override
    public String describe()
    {
        return "Native access is not available for current platform.";
    }
}
