/*
 * Copyright (c) 2012. Paolo Di Tommaso.
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

package blow.util

import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.JSch
import groovy.util.logging.Slf4j
import blow.exception.BlowConfigException

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class KeyPairBuilder {

    def type

    def passPhrase = ''

    def comment = ''

    def fingerPrint

    def File privateKeyFile

    def File publicKeyFile

    /**
     * This class is supposed to be create using the {@link #create} method, so the
     * constructor is declared {@code protected}
     */
    protected KeyPairBuilder( ) {

    }

    /**
     * Factory method
     * @return An instance of the class
     */
    static KeyPairBuilder create() {
        new KeyPairBuilder()
    }

    /**
     * Defines the file to be used to store the private key.
     * Moreover:
     * <li>
     * if the public key not has been already defined, it will set the public key file to the default name e.g.
     * the same as the private key with the {@code .pub} suffix
     * </li>
     *
     * <li>
     * if the key type not has been defined, it will try to infer the type from the private key file name e.g.
     * id_rsa --> rsa - or - id_dsa --> dsa
     * </li>
     *
     * @param privateFile
     * @return
     */
    KeyPairBuilder privateKey( def privateFile ) {
        assert privateFile

        // normalize the private key file class
        if( !(privateFile instanceof File) ) {
            this.privateKeyFile = new File(privateFile.toString())
        }
        else {
            this.privateKeyFile = privateFile
        }

        /*
         * define as well the public key is not defined already
         */
        if( !this.publicKeyFile ) {
            publicKey( privateFile.toString()+".pub" )
        }

        /*
         * set the type if not defined already
         */
        if( !type ) {

            if( privateFile.toString().endsWith("_rsa")) {
                type = "rsa"
            }
            else if( privateFile.toString().endsWith("_dsa")) {
                type = 'dsa'
            }

        }


        return this
    }


    KeyPairBuilder publicKey( def publicKey ) {
        assert publicKey
        if( !(publicKey instanceof File ) ) {
            this.publicKeyFile = new File(publicKey.toString())
        }
        else {
            this.publicKeyFile = publicKey
        }

        return this
    }

    KeyPairBuilder type( String type ) {
        this.type = type
        return this
    }

    KeyPairBuilder comment( String value ) {
        this.comment = value
        return this
    }

    KeyPairBuilder passPhrase( String value ) {
        this.passPhrase = value
        return this
    }



    /*
    * Read more
    * http://www.jcraft.com/jsch/examples/KeyGen.java.html
    * http://stackoverflow.com/questions/3706177/how-to-generate-ssh-compatible-id-rsa-pub-from-java
    * http://java.sun.com/developer/onlineTraining/Security/Fundamentals/Security.html
    */

    KeyPairBuilder store( ) {

        assert privateKeyFile

        /*
         * default public key file if not defined
         */
        if( !publicKeyFile ) {
            publicKeyFile = new File(privateKeyFile.toString() + '.pub')
        }

        /*
        * make sure parent files exists
        */
        if( privateKeyFile.getParentFile() && !privateKeyFile.getParentFile().exists() && !privateKeyFile.getParentFile().mkdirs() ) {
            throw new RuntimeException("Cannot create file path: ${privateKeyFile.getParentFile()}")
        }

        if( publicKeyFile.getParentFile() && !publicKeyFile.getParentFile().exists() && !publicKeyFile.getParentFile().mkdirs() ) {
            throw new BlowConfigException("Cannot create file path: ${publicKeyFile.getParentFile()}")
        }


        /*
         * verify key type
         */
        if( !(type in ['rsa','dsa']) ) {
            log.warn "The specified key type is not valid '${type}'. Using 'rsa' by defualt."
            type = 'rsa'
        }

        /*
         * create the keys
         */
        KeyPair keyPair=KeyPair.genKeyPair(new JSch(), type == 'dsa' ? KeyPair.DSA : KeyPair.RSA);
        keyPair.setPassphrase(passPhrase);
        keyPair.writePrivateKey(privateKeyFile.toString());
        keyPair.writePublicKey(publicKeyFile.toString(), "");

        fingerPrint = keyPair.getFingerPrint()

        keyPair.dispose();

        /*
        * Give r/w permission only to the current user
        */
        try {
            privateKeyFile.setReadable(false,false)
            privateKeyFile.setWritable(false,false)
            privateKeyFile.setReadable(true,true)
            privateKeyFile.setWritable(true,true)
        }
        catch( Exception e ) {
            def os = System.getProperty("os.name")
            if( !os?.startsWith("Windows") ){
                 log.debug "Cannot set attributes to file: '$privateKeyFile'; OS: '$os'"
            }

        }


        return this
    }

}
