/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 */

package org.seaborne.jena.tdb.base.file;

import org.apache.jena.atlas.lib.FileOps ;
import org.junit.AfterClass ;
import org.seaborne.jena.tdb.ConfigTest ;
import org.seaborne.jena.tdb.base.file.BufferChannel ;
import org.seaborne.jena.tdb.base.file.BufferChannelFile ;

public class TestChannelFile extends AbstractTestChannel
{
    static String filename = ConfigTest.getTestingDir()+"/test-storage" ;

    @AfterClass public static void cleanup() { FileOps.deleteSilent(filename) ; } 
    
    @Override
    protected BufferChannel open()
    {
        FileOps.deleteSilent(filename) ;
        return BufferChannelFile.create(filename) ;
    }
}
