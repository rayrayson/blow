/*
 * Copyright (c) 2012, the authors.
 *
 *   This file is part of Blow.
 *
 *   Blow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Blow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Blow.  If not, see <http://www.gnu.org/licenses/>.
 */

package blow.command

import blow.BlowSession
import blow.exception.CommandSyntaxException
import blow.shell.BlowShell
import blow.shell.Cmd
import blow.shell.CmdFree
import blow.util.PromptHelper
import com.google.common.collect.ImmutableSet
import com.google.inject.Module
import groovy.util.logging.Slf4j
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.jclouds.aws.s3.blobstore.strategy.internal.MultipartUploadSlicingAlgorithm
import org.jclouds.blobstore.AsyncBlobStore
import org.jclouds.blobstore.BlobStore
import org.jclouds.blobstore.BlobStoreContext
import org.jclouds.blobstore.BlobStoreContextFactory
import org.jclouds.blobstore.domain.Blob
import org.jclouds.blobstore.domain.StorageMetadata
import org.jclouds.blobstore.options.ListContainerOptions
import org.jclouds.blobstore.options.PutOptions
import org.jclouds.domain.Location
import org.jclouds.domain.LocationBuilder
import org.jclouds.domain.LocationScope
import org.jclouds.http.apachehc.config.ApacheHCHttpCommandExecutorServiceModule
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule
import org.jclouds.netty.config.NettyPayloadModule

import java.text.DecimalFormat
import javax.ws.rs.core.MediaType
import blow.BlowConfig
import blow.shell.CmdParams
import com.beust.jcommander.Parameter

/**
 *
 * Read more about JClouds blob store here
 * http://www.jclouds.org/documentation/userguide/blobstore-guide/
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Mixin(PromptHelper)
class S3commands {

    static final def provider = "aws-s3"

    transient final static Iterable<? extends Module> MODULES =
        ImmutableSet.of( new ApacheHCHttpCommandExecutorServiceModule(), new SLF4JLoggingModule(), new NettyPayloadModule());

    transient private BlowShell shell
    transient private BlowSession session

    transient static private BlobStoreContext context
    transient static private def currentProps
    transient static private Map<BlowConfig,Properties> propertiesMap = [:]

    @Lazy
    transient private BlowConfig shellConf = { shell.configBuilder.buildConfig(null) }( )


    private static getProps(BlowConfig config) {
        def result = propertiesMap[config]
        if( !result ) {
            result = new Properties()
            //overrides.setProperty("jclouds.mpu.parallel.degree", '8'); // without setting, default is 4 threads
            //
            result.setProperty(provider + ".identity", config.accessKey);
            result.setProperty(provider + ".credential", config.secretKey);

            // cache this properties
            propertiesMap[config, result]
        }

        return result
    }


    /**
     * Lazy getter for the S3 storage context (i.e. the connection)
     */
    private BlobStoreContext ctx() {

        BlowConfig config = session ? session.conf : shellConf
        def props = getProps(config)

        if( !context || props != currentProps ) {
            log.debug "Creating new S3 context"

            def result = new BlobStoreContextFactory().createContext(provider, MODULES, props)

            if( context ) {
                log.debug "Closing previous S3 context"
                context.close()
            }
            context = result
            currentProps = props
        }


        return context

    }

    /**
     * Parameters for S3 'list' command
     */
    static class ListParams extends  CmdParams {

        @Parameter(names='-r', description='List S3 content recursively')
        Boolean recursive;

        @Parameter
        List<String> args

    }

    /**
     * List of the buckets in the S3 account
     */
    @Cmd(usage='s3ls [options] [path]', summary='List the content of your S3 storage')
    def void s3ls ( ListParams params )
    {

        def path = params.args ? params.args[0] : null

        if( path?.startsWith('s3://') ) {
            path = path.substring('s3://'.length())
        }

        def result = list( path, params.recursive==true )

        /*
         * Print the result
         */
        if( !result ) {
            println "(nothing to list)"
            return
        }

        result.each { S3Object item ->
            print 's3://'
            println item.getPath()
        }

    }

    private list( String path, boolean recursive=false, int maxCount = 1000, List<S3Object> resultSet = new LinkedList<S3Object>() ) {
        log.debug "S3 List path: $path; recursive: $recursive; maxCount: $maxCount"

        def store = ctx().getBlobStore()

        /*
         * List only the bucket
         */
        if( !path && !recursive ) {
            def containers = store.list()
            for( StorageMetadata it : containers ) {
                resultSet.add( new S3Object(it.getName(), it))
            }
            return resultSet
        }
        /*
         * Get the list of buckets on deep on them recursively
         */
        else if( !path && recursive ) {
            def containers = store.list()
            for( StorageMetadata it : containers ) {
                if( resultSet.size() > maxCount ) { break }
                resultSet.add( new S3Object(it.getName(), it))
                list(it.getName(), true, maxCount, resultSet )
            }
            return resultSet
        }

        /*
         * list recursively the specified path
         */
        else {
            def loc = S3Path.split(path)
            def opt = ListContainerOptions.Builder.maxResults(maxCount)
            if( recursive ) {
                opt.recursive()
            }
            if( loc.directory ) {
                opt.inDirectory(loc.directory)
            }

            def entries = store.list(loc.container, opt )

            int count = resultSet.size()
            for( StorageMetadata it : entries ) {
                if( count++ > maxCount ) { break }
                resultSet.add( new S3Object(loc.format(it.getName()), it))
            }

        }


        return resultSet
    }

    /**
     * Parameter class for 's3cp' command
     */
    static class CopyParams extends CmdParams {

        @Parameter(names='--region', description='When copying to a new bucket, specify the region where it have to be created')
        String regionId;

        @Parameter
        List<String> files

    }

    /**
     * S3 copy command
     *
     * @param params
     */
    @Cmd(summary='Copy a file to/from S3 Storage')
    def void s3cp ( CopyParams params )
    {
        def source = params.files ? params.files[0] : null
        def target = params.files.size()>1 ? params.files[1] : null

        if( !source ) { throw new CommandSyntaxException("Please specify the source object") }
        if( !target ) { throw new CommandSyntaxException("Please specify the target object") }

        /*
        * Get a file from S3 and copy here
        */
        if( source.startsWith('s3://') ) {

            def targetFile = strToFile(target)

            def result = getFromS3(source, targetFile)
            if( result != targetFile ) {
                println "Stored location: " + result.canonicalPath
            }
            return
        }

        /*
         * PUT a file into a S3 bucket
         */
        if( target.startsWith('s3://') )  {

            if( target == 's3://' ) { throw new CommandSyntaxException('Specify a S3 conatiner e.g. s3://some-bucket') }

            def file = strToFile(source)
            if( !file.exists() ) {
               println "The file you have specified does not exists: '$file'"
               return
            }

            if( !file.isFile() ) {
               println "The source item must be a file: '$file'"
               return
            }

            copyToS3(file, target, params.regionId)
            return
        }


        throw new CommandSyntaxException("One resource in your command must begin with 's3://'")


    }

    /*
     * Upload a file to the S3 storage
     */
    private copyToS3( File input, String containerName, String regionId = null)
    {
        assert input
        assert containerName

        if( containerName.startsWith('s3://') ) {
            containerName = containerName.substring('s3://'.length())
        }

        def loc = defaultLocation(regionId)
        print "Uploading '${input.getName()}' (region: ${loc.getId()}) ..."

        def objectName = input.name
        def start = System.currentTimeMillis()

        /*
         * In JClouds there are two uplaod strategy:
         * - SequentialMultipartUploadStrategy, used by the standard BlobStore
         * - ParallelMultipartUploadStrategy, used by the AsycnBlobStore
         */
        AsyncBlobStore store = ctx().getAsyncBlobStore();

        store.createContainerInLocation(loc, containerName).get()  // note: the get is required to wait for the creation of the bucket

        // Add a Blob
        Blob blob = store
                    .blobBuilder(objectName)
                    .payload(input)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentDisposition(objectName)
                    .build();

        def opt
        long length = input.length();
        //NOTE: the chunck size can be controlled by the property 'jclouds.mpu.parts.size'
        if( length <= MultipartUploadSlicingAlgorithm.DEFAULT_PART_SIZE ) {
            // there's a bug that make the multipart upload hand if there's only ONE  chunck
            // so disabled it.
            opt = PutOptions.NONE
        }
        else {
            opt = PutOptions.Builder.multipart()
        }

        // Upload a file
        def result = store.putBlob(containerName, blob, opt);
        result.get()

        printSpeed("\rComplete!", start, length);
    }

    /*
     * Download a file from the S3 storage
     *
     * @param path A fully qualified S3 location e.g. /bucket/directory/to/file
     * @param target A local file where to save the downloaded file
     * @return The file where the download has been file (it may change if the 'target' parameter was a directory)
     */
    private File getFromS3( String path, File target ) {
        assert path
        log.debug "S3 download: $path to: $target "

        if( path.startsWith('s3://') ) {
            path = path.substring('s3://'.length())
        }

        def fileName = FilenameUtils.getName(path)
        print "Downloading '$fileName' ..."

        if( target.isDirectory() )  {
            target = new File(target, fileName)
        }

        /*
         * The real job starts here
         */
        def start = System.currentTimeMillis()
        BlobStore store = ctx().getBlobStore();

        def location = S3Path.split(path)
        Blob blob = store.getBlob(location.container, location.directory)

        def out = new FileOutputStream(target)
        IOUtils.copyLarge(blob.getPayload().getInput(), out)
        out.close()

        printSpeed("\rComplete!", start, target.size())

        return target

    }

    private Location defaultLocation( String regionId = null ) {

        if( !regionId && session ) {
            regionId = session.conf.regionId
        }

        if( !regionId ) {
            return null
        }

        if( regionId == 'us-east-1' ) {
            //  .. since 'us-east-1' in not included in the list {@link Region#DEFAULT_S3}
            regionId = 'us-standard'
        }

        return new LocationBuilder().scope(LocationScope.REGION).id(regionId).description(regionId).build()

    }

    static DecimalFormat FORMATTER = new DecimalFormat("###,###.#")

    static fmt( def num ) {
        def result = FORMATTER.format(num)
        result.replace(',',"'")
    }

    static void printSpeed(String message, long start, long length) {
        long sec = (System.currentTimeMillis() - start) / 1000;
        if (sec == 0)
            return;
        long speed = length / sec;
        System.out.print(message);

        if (speed < 1024) {
            System.out.print(" ${fmt(length)} bytes");
        }
        else if (speed < 1048576) {
            System.out.print(" ${fmt(length / 1024)} KB");
        }
        else if (speed < 1073741824) {
            System.out.print(" ${fmt(length / 1048576)} MB");
        }
        else {
            System.out.print(" ${fmt(length / 1073741824)} GB");
        }
        System.out.println(" with ${getSpeed(speed)} (${fmt(length)} bytes)");
    }

    static String getSpeed(long speed) {
        if (speed < 1024) {
            return "${fmt(speed)} bytes/s"
        }
        else if (speed < 1048576) {
            return "${fmt(speed / 1024)} KB/s";
        }
        else {
            return "${fmt(speed / 1048576)} MB/s";
        }
    }


    @CmdFree
    def void close() {
       if( context ) {
           context.close()
           context = null // <-- don't forget since the close invoked for each command for this class
       }
    }


    static File strToFile( String str ) {
        if( !str ) {
            return new File('.')
        }

        if( str == '~' ) {
            return new File(System.properties['user.home'])
        }

        if( str.startsWith('~/') ) {
            return new File( System.properties['user.home'], str.substring(1) )
        }

        return new File(str)

    }
}


class S3Path {
    String container
    String directory

    String format( String name ) {
        def result = []
        if( container ) result << container
        if( directory ) result << directory
        if( name ) result << name

        return result.join('/')
    }


    def void setContainer( String value  ) {
        if( value ?. startsWith('/') ) value = value.substring(1)
        if( value ?. endsWith('/') ) value = value.substring(0,value.length()-1)
        container = value
    }

    def void setDirectory( String value ) {
        if( value ?. startsWith('/') ) value = value.substring(1)
        if( value ?. endsWith('/') ) value = value.substring(0,value.length()-1)
        directory = value
    }

    static S3Path split( String path ) {
        S3Path result = new S3Path(container:'', directory:'')

        if( !path ) return result


        if( path.startsWith('/')) path = path.substring(1)

        int pos = path.indexOf('/')
        if( pos == -1 ) {
            result.container = path
        }
        else {
            result.container = path.substring(0,pos)
            result.directory = path.substring(pos+1)
        }

        return result
    }
}


class S3Object {

    private path

    @Delegate
    private StorageMetadata metadata

    S3Object( String path, StorageMetadata metadata ) {
        this.path = path
        this.metadata = metadata
    }

    String getPath() { path }

}