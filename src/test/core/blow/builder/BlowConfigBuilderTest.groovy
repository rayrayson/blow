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

package blow.builder

import blow.BlowConfig
import blow.exception.BlowConfigException
import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlowConfigBuilderTest extends Specification {

    def "config " () {

        when:
        def conf = new BlowConfigBuilder()._ {

            imageId 'ami-4343'
            instanceType 't1.micro'
            accessKey 'xxx'
            secretKey 'yyy'

        }

        then:
        conf instanceof BlowConfig
        conf.imageId == 'ami-4343'
        conf.instanceType == 't1.micro'
        conf.accessKey == 'xxx'
        conf.secretKey == 'yyy'

    }

    def "config roles" () {
        when:
        def conf = new BlowConfigBuilder()._ {

            roles 'one-role'

        }

        then:
        conf instanceof BlowConfig
        conf.roles == ['one-role']
    }

    def "config roles (2)" () {
        when:
        def conf = new BlowConfigBuilder()._ {

            roles 'one', 'two'

        }

        then:
        conf instanceof BlowConfig
        conf.roles == ['one','two']
    }

    def "missing exception" () {
        when:
        def conf = new BlowConfigBuilder()._root {
            imageId 'OK'
            missingProperty 'xx'
        }

        then:
        thrown(BlowConfigException)
    }


    def "conf with section" () {

        when:
        def conf = new BlowConfigBuilder('cluster1')._root {

            imageId 'ami-4343'
            instanceType 't1.micro'
            accessKey 'xxx'
            secretKey 'yyy'

            cluster1 {
                imageId 'ami-111'
                accessKey '9999'

            }

            cluster2 {
                instanceType 't2.type'

            }

        }

        then:
        conf instanceof BlowConfig
        conf.imageId == 'ami-111'
        conf.instanceType == 't1.micro'
        conf.accessKey == '9999'
        conf.secretKey == 'yyy'

    }


    def "conf operations " () {
        when:
        def conf = new BlowConfigBuilder('cluster1')._root {

            cluster1 {
                imageId 'ami-111'
                accessKey '9999'

                operations ('master') {
                    hostname()
                }

                operations('slave') {
                    volume(device:'/dev/x')
                    volume(device:'/dev/y')
                }

            }

        }

        then:
        conf instanceof BlowConfig
        conf.imageId == 'ami-111'
        conf.operations.size() == 3
        conf.operations[0].applyTo == 'master'
        conf.operations[1].applyTo == 'slave'
        conf.operations[1].device == '/dev/x'
        conf.operations[2].applyTo == 'slave'
        conf.operations[2].device == '/dev/y'
    }

    def "test s3cmd" () {

        when:
        def conf = new BlowConfigBuilder('test-s3cmd').conf {

            accountId '8888888'
            accessKey 'ZZZZZZZ'
            instanceType 't1.micro'

            'test-s3cmd' {
                keyPair 'maquito'
                userName 'ec2-user'
                instanceNum 1
                operations {
                    s3cmd()
                }
            }

        }

        then:
        conf.keyPair == 'maquito'
        conf.userName == 'ec2-user'
        conf.instanceNum == 1
        conf.operations.size() == 1
    }


    def "test vols" () {

        when:
        def conf = new BlowConfigBuilder('test-vol').conf {

            'test-vol' {
                instanceNum (master:1, worker: 99)

                operations {
                    volume (path: '/newvol', size: 1, makeSnapshotOnTermination: true)
                    volume (path: '/data', volumeId: 'vol-58490330' )
                    volume (path: '/soft', snapshotId: 'snap-bd5d56d6', deleteOnTermination: true )
                }
            }

        }


        then:
        conf.instanceNum.master == 1
        conf.instanceNum.worker == 99
        conf.operations.size() == 3

        conf.operations[0].path == '/newvol'
        conf.operations[0].size == 1
        conf.operations[0].makeSnapshotOnTermination == true

        conf.operations[1].path == '/data'
        conf.operations[1].volumeId == 'vol-58490330'

        conf.operations[2].path == '/soft'
        conf.operations[2].snapshotId == 'snap-bd5d56d6'
        conf.operations[2].deleteOnTermination == true

    }


    def "test names "() {

        when:
        def builder = new BlowConfigBuilder('test1');

        builder.conf {
            imageId 'xxx'
            instanceNum 11

            test1 {
                instanceType 'a'
            }

            test2 {
                instanceType 'b'
            }

            test3 {
                instanceType 'c'
            }

        }


        then:
        builder.clusterNames.size() == 3
        builder.clusterNames == ['test1','test2','test3']
        builder.config.imageId == 'xxx'
        builder.config.instanceNum == 11
        builder.config.instanceType == 'a'
    }


    def "test create" () {
        setup:

        def CONFIG = """\
        imageId 'ami-123'
        instanceType 'micro'
        userName "\$USER"
        """
        .stripIndent()

        when:
        def builder = BlowConfigBuilder.create(CONFIG)

        then:
        builder.config.imageId == 'ami-123'
        builder.config.instanceType == 'micro'
        builder.config.userName == System.getenv('USER')

    }

    def "test create multiple" () {


        setup:
        def CONFIG1 = """\
        accessKey "123"
        secretKey "xxx"
        regionId "eu-west"
        """

        def CONFIG2 = """\
        imageId 'ami-123'
        instanceType 'micro'

        cluster1 {
            imageId "ami-111"
            instanceNum 10
        }

        cluster2 {
            imageId "ami-222"
            instanceType "macro"
        }
        """
        .stripIndent()

        when:
        def builder = BlowConfigBuilder.create(CONFIG1, CONFIG2)
        def c1 = builder.buildConfig('cluster1')
        def c2 = builder.buildConfig('cluster2')

        then:
        c1.accessKey == "123"
        c1.secretKey == "xxx"
        c1.regionId == "eu-west"
        c1.imageId == "ami-111"
        c1.instanceNum == 10
        c1.instanceType == "micro"

        c2.accessKey == "123"
        c2.secretKey == "xxx"
        c2.regionId == "eu-west"
        c2.imageId == "ami-222"
        c2.instanceType == "macro"

    }
}

