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

package org.seaborne.jena.tdb.sys;

import java.nio.ByteOrder ;

import org.seaborne.jena.tdb.TDBException ;
import org.seaborne.jena.tdb.base.block.FileMode ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class SystemIndex
{
    // NB Same logger as the TDB class because this class is the system info but kept out of TDB javadoc.
    // It's visibility is TDB, not really public. 
    private static final Logger log = LoggerFactory.getLogger("Base") ;
    
    /** TDB System log - use for general messages (a few) and warnings.
     *  Generally, do not log events unless you want every user to see them every time.
     *  TDB is an embedded database - libraries and embedded systems should be seen and not heard.
     *  @see #errlog 
     */
    // This was added quite late in TDB so need to check it's used appropriately - check for Log.*
    public static final Logger syslog = LoggerFactory.getLogger("System") ;
    /** Send warnings and error */
    public static final Logger errlog = LoggerFactory.getLogger("System") ;
    
    // ---- Constants that can't be changed without invalidating on-disk data.  
    
    /** Size, in bytes, of a Java long */
    public static final int SizeOfLong              = Long.SIZE/Byte.SIZE ;
    
    /** Size, in bytes, of a Java int */
    public static final int SizeOfInt               = Integer.SIZE/Byte.SIZE ;
    
    /** Size, in bytes, of a pointer between blocks */
    public static final int SizeOfPointer           = SizeOfInt ;
    
    public static final boolean is64bitSystem = SystemLz.is64bitSystem ;

    // To make the class initialize
    static public void init() {}
    
    /** Size, in bytes, of a block */
    public static final int BlockSize               = 8*1024 ; // intValue("BlockSize", 8*1024) ;

    /** Size, in bytes, of a block for testing */
    public static final int BlockSizeTest           = 1024 ; // intValue("BlockSizeTest", 1024) ;

    /** Size, in bytes, of a block for testing */
    public static final int BlockSizeTestMem         = 500 ;

//    /** Size, in bytes, of a memory block */
//    public static final int BlockSizeMem            = 32*8 ; //intValue("BlockSizeMem", 32*8 ) ;

    /** order of an in-memory BTree or B+Tree */
    public static final int OrderMem                = 5 ; // intValue("OrderMem", 5) ;
    
    /** Size, in bytes, of a segment (used for memory mapped files) */
    public static final int SegmentSize             = 8*1024*1024 ; // intValue("SegmentSize", 8*1024*1024) ;
    
    // ---- Cache sizes (within the JVM)
    
    public static final int ObjectFileWriteCacheSize = 8*1024 ;
    
    /** Size of Node to NodeId cache.
     *  Used to map from Node to NodeId spaces.
     *  Used for loading and for query preparation.
     */
    public static final int Node2NodeIdCacheSize    = intValue("Node2NodeIdCacheSize", ( is64bitSystem ? 100*1000 : 50*1000 )) ;

    /** Size of NodeId to Node cache.
     *  Used to map from NodeId to Node spaces.
     *  Used for retriveing results.
     */
    public static final int NodeId2NodeCacheSize    = intValue("NodeId2NodeCacheSize", ( is64bitSystem ? 500*1000 : 50*1000 ) ) ;
    
    /** Size of Node lookup miss cache. */
    public static final int NodeMissCacheSize       = 100 ;
    
    /** Size of the delayed-write block cache (32 bit systems only) (per file) */
    public static final int BlockWriteCacheSize     = intValue("BlockWriteCacheSize", 2*1000) ;

    /** Size of read block cache (32 bit systems only).  Increase JVM size as necessary. Per file. */
    public static final int BlockReadCacheSize      = intValue("BlockReadCacheSize", 10*1000) ;
    
    private static int intValue(String name, int dft) { return dft ; }
    
    // ---- Misc
    
//    /** Number of adds/deletes between calls to sync (-ve to disable) */
//    public static final int SyncTick                = intValue("SyncTick", -1) ;

//    /** Default BGP optimizer */
//    public static ReorderTransformation defaultReorderTransform = ReorderLib.fixed() ;

    public static final ByteOrder NetworkOrder      = ByteOrder.BIG_ENDIAN ;
    
    public static void setNullOut(boolean nullOut)
    { NullOut = nullOut ; }

    /** Are we nulling out unused space in bytebuffers (records, points etc) */ 
    public static boolean getNullOut()
    { return NullOut ; }

    /** null out (with the FillByte) freed up space in buffers */
    public static boolean NullOut = false ;
    
    /** FillByte value for NullOut */
    public static final byte FillByte = (byte)0xFF ;

    public static boolean Checking = false ;       // This isn't used enough!
    
    // ---- File mode
    
    private static FileMode fileMode = null ;
    public static FileMode fileMode()
    { 
        if ( fileMode == null )
            fileMode = determineFileMode() ;
        return fileMode ;
    }

    public static void setFileMode(FileMode newFileMode)
    {
        if ( fileMode != null )
        {
            log.warn("System file mode already determined - setting it has no effect") ;
            return ;
        }
        fileMode = newFileMode ;
    }
    
    // So the test suite can setup thing up ... very carefully.
    /*package*/ static void internalSetFileMode(FileMode newFileMode)
    {
        fileMode = newFileMode ;
    }
    
    private static FileMode determineFileMode()
    {
        // Be careful that this is not called very, very early, before --set might be seen.
        // Hence delayed access above in fileMode().
        
        //String x = ARQ.getContext().getAsString(SystemTDB.symFileMode, "default") ;
        String x = "default" ;
        
        if ( x.equalsIgnoreCase("direct") )
        {
            syslog.info("File mode: direct (forced)") ;
            return FileMode.direct ;
        }
        if ( x.equalsIgnoreCase("mapped") )
        {
            syslog.info("File mode: mapped (forced)") ;
            return FileMode.mapped ;
        }
        
        if ( x.equalsIgnoreCase("default") )
        {
            if ( is64bitSystem )
            {
                syslog.debug("File mode: Mapped") ;
                return FileMode.mapped ;
            }
            syslog.debug("File mode: Direct") ;
            return FileMode.direct ;
        }
        throw new TDBException("Unrecognized file mode (not one of 'default', 'direct' or 'mapped': "+x) ;
    }
}
