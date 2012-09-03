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
import blow.shell.Cmd
import blow.shell.CmdParams
import com.beust.jcommander.Parameter
import groovy.util.logging.Slf4j
import org.jclouds.ec2.domain.KeyPair
import org.jclouds.aws.AWSResponseException

/**
 *  Provides commands to manage key-pairs
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class KeysCommands {

    BlowSession session

    @Cmd( name='listkeypairs', summary='List the key-pairs available in the current region' )
    def void listKeyPairs ( )
    {
        def region = session.conf.regionId
        def list = session.keyPairClient.describeKeyPairsInRegion(region)
        if( !list ) {
            println "(no key-pairs found in region: $region)"
            return
        }

        list.each { KeyPair key -> println key.keyName }
    }

    /**
     * Class to hold parameter for 'Create KeyPair' command
     */
    static class CreateKeyPairParams extends CmdParams {

        @Parameter(names='-s', description='Store the key-pair in the current working directory')
        Boolean store;

        @Parameter
        List<String> args

    }

    /**
     * Create a new key in the current region
     *
     * @param keyName The name of the key to be created
     */
    @Cmd( name='createkeypair', usage='createkeypair [options] keypair-name', summary='Create a new key-pair in the current region' )
    def void createKeyPair( CreateKeyPairParams params ) {

        def keyName = params.args ? params.args[0] : null

        if( !keyName ) {
            throw new CommandSyntaxException('Please specified to name of the key-pair to be created')
        }

        /*
         * Verify is a file with the same name already exists
         */
        def keyFile = new File("${keyName}.pem")
        if( params.store && keyFile.exists() ) {

            if( 'y' == session.promptYesOrNo('A key-pair file with the same name already exists. Do you want to overwite it?') ) {
               keyFile.delete()
            }
            else {
                // user do not to overwrite the key-pair that exists with the same name
                // abort the command
                return
            }

        }

        /*
         * Create the kay-pair
         */
        def keyPair = session.keyPairClient.createKeyPairInRegion(session.conf.regionId, keyName)

        if( !keyPair ) {
            println "Unable to create a new key-pair named: " + params.keyName
            return
        }

        if( params.store ) {
            if( !keyFile.exists() ) keyFile.createNewFile()
            keyFile.setText( keyPair.keyMaterial )
            setFileTo600(keyFile)
            println "Key-pair file stored to path '${keyFile.absolutePath}'"
        }
        else {
            println keyPair.keyMaterial
        }

    }

    /**
     * Parameter class for 'delete key-pair' command
     */

    static class DeleteKeyPairParams extends CmdParams {

        @Parameter
        List<String> keyNames
    }

    /**
     * Delete the key with the provided name
     *
     * @param keyName
     */
    @Cmd(name='deletekeypair', summary='Delete a key-pair from the current configured region')
    def void deleteKeyPair(DeleteKeyPairParams params) {

        if( !params.keyNames ) {
            throw new CommandSyntaxException('Please specified to name of the key-pair to be deleted')
        }

        /*
         * find out the keyNames to delete
         */
        def keyNames = []
        def pattern = params.keyNames.join('|')

        session.keyPairClient.describeKeyPairsInRegion(session.conf.regionId).each { KeyPair key ->
            if( key.keyName ==~ /$pattern/) {
                keyNames << key.keyName
            }
        }

        if( keyNames.size()==0 ) {
            println "No key-pair matches the specified pattern: '${pattern}'"
            return
        }

        deleteKeyPairWithNames(keyNames)

    }



    private void deleteKeyPairWithNames(List<String> keyNames) {
        assert keyNames

        def message
        def count=0
        if( keyNames.size()>1 ) {
            message = "The following Key-pairs will be deleted: "
            keyNames.each { message += "\n* ${it}" }
        }
        else {
            message = "The following Key-pair will be deleted: ${keyNames[0]}"
        }
        message += "\nPlease confirm the delete opeation"

        def answer = session.promptYesOrNo(message)
        if( answer != 'y') {
            return
        }

        keyNames.each {
            try {
                session.keyPairClient.deleteKeyPairInRegion(session.conf.regionId,it)
            }
            catch( AWSResponseException e ) {
                log.warn("Error deleting key-pair '${it}'", e)
            }
        }

        println "Done"
    }


    static def void setFileTo600( File file ) {
        file.setReadable(false)
        file.setWritable(false)
        file.setReadable(true,true)
        file.setWritable(true,true)
    }

    static def void saveKeyPair( KeyPair keypair )  {
        File file = new File("${keypair.keyName}.pem")
        if( !file.exists() ) {
            file.createNewFile()
        }
        file.setText( keypair.keyMaterial )

        setFileTo600(file)
    }

}
