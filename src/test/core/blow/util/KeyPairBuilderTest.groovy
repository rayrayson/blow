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

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class KeyPairBuilderTest extends Specification {

    def "test properties " () {

        when:
        def builder = KeyPairBuilder.create().privateKey("/some/path/id_dsa")

        then:
        builder.privateKeyFile == new File("/some/path/id_dsa")
        builder.publicKeyFile == new File("/some/path/id_dsa.pub")
        builder.type == 'dsa'

    }



    def "test properties_2 " () {

        when:
        def builder = KeyPairBuilder.create()
                    .type('rsa')
                    .privateKey("/some/path/private")
                    .publicKey("/some/other/path/key_pem")

        then:
        builder.privateKeyFile == new File("/some/path/private")
        builder.publicKeyFile == new File("/some/other/path/key_pem")
        builder.type == 'rsa'

    }

    def "test properties_as_files " () {

        when:
        def builder = KeyPairBuilder.create()
                .privateKey(new File("/some/path/private_file_dsa"))
                .publicKey(new File("/some/other/path/key_pem"))

        then:
        builder.privateKeyFile == new File("/some/path/private_file_dsa")
        builder.publicKeyFile == new File("/some/other/path/key_pem")
        builder.type == 'dsa'

    }


    def "test properties_3" () {
        when:
        def builder = KeyPairBuilder.create().comment("Hola").passPhrase("Pippo")

        then:
        builder.comment == 'Hola'
        builder.passPhrase == 'Pippo'
    }


    def "test store "() {
        setup:
        def privKey = new File("./test/keys/testPrivateKey")
        def pubKey = new File("./test/keys/testPublibKey")

        if( privKey.exists() ) privKey.delete()
        if( pubKey.exists() ) pubKey.delete()

        assert !privKey.exists()
        assert !pubKey.exists()

        when:
        def builder = KeyPairBuilder.create().type('rsa').privateKey(privKey).publicKey(pubKey).store()

        then:
        privKey.exists()
        privKey.text.startsWith("-----BEGIN RSA PRIVATE KEY-----")

        pubKey.exists()
        pubKey.text.startsWith("ssh-rsa")


    }


    def "test store RSA"() {
        setup:
        def privKey = new File("./test/keys/id_rsa")
        def pubKey = new File("./test/keys/id_rsa.pub")

        if( privKey.exists() ) privKey.delete()
        if( pubKey.exists() ) pubKey.delete()

        assert !privKey.exists()
        assert !pubKey.exists()

        when:
        def builder = KeyPairBuilder.create().privateKey(privKey).publicKey(pubKey).store()

        then:
        privKey.exists()
        privKey.text.startsWith("-----BEGIN RSA PRIVATE KEY-----")

        pubKey.exists()
        pubKey.text.startsWith("ssh-rsa")

    }

    def "test store DSA"() {
        setup:
        def privKey = new File("./test/keys/id_dsa")
        def pubKey = new File("./test/keys/id_dsa.pub")

        if( privKey.exists() ) privKey.delete()
        if( pubKey.exists() ) pubKey.delete()

        assert !privKey.exists()
        assert !pubKey.exists()

        when:
        def builder = KeyPairBuilder.create().privateKey(privKey).publicKey(pubKey).store()

        then:
        privKey.exists()
        privKey.text.startsWith("-----BEGIN DSA PRIVATE KEY-----")

        pubKey.exists()
        pubKey.text.startsWith("ssh-dss")

    }
}
