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
import blow.shell.Opt
import org.jclouds.ec2.domain.KeyPair
import org.jclouds.aws.AWSResponseException
import groovy.util.logging.Slf4j

/**
 *  Provides command to manage key-pairs
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class KeysCommands {

    BlowSession session

    @Cmd( summary='List the key-pairs available in the current region' )
    def void listkeypairs ( )
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
     * Create a new key in the current region
     *
     * @param keyName The name of the key to be created
     */
    @Cmd( usage='createkeypair [options] keypair-name', summary='Create a new key-pair in the current region' )
    def void createkeypair( @Opt(opt='s', description='Store the key-pair in the current working directory') Boolean store, String keyName ) {
        if( !keyName ) {
            throw CommandSyntaxException('Please specified to name of the key-pair to be created')
        }

        /*
         * Verify is a file with the same name already exists
         */
        def keyFile = new File("${keyName}.pem")
        if( store && keyFile.exists() ) {

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
            println "Unable to create a new key-pair named: " + keyName
            return
        }

        if( store ) {
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
     * Delete the key with the provided name
     * @param keyName
     */
    @Cmd(usage='deletekeypair keypair-name [key-pair name..]', summary='Delete a key-pair from the current configured region')
    def void deletekeypair(List<String> keyNames) {
        if( !keyNames ) {
            throw CommandSyntaxException('Please specified to name of the key-pair to be deleted')
        }

        def answer = session.promptYesOrNo("Please confirm to delete the key-pairs with name '${keyNames.join(', ')}'" )
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
