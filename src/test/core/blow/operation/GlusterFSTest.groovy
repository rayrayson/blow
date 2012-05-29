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

package blow.operation

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class GlusterFSTest extends Specification {

    def "test getInstallScript" () {
        when:
        def gluster = new GlusterFS()
        def expected = """\
        # Install required dependencies
        yum -y install wget fuse fuse-libs

        # Download and install the Gluster components
        wget -q http://download.gluster.org/pub/gluster/glusterfs/3.2/3.2.4/Fedora/glusterfs-core-3.2.4-1.fc11.x86_64.rpm
        wget -q http://download.gluster.org/pub/gluster/glusterfs/3.2/3.2.4/Fedora/glusterfs-fuse-3.2.4-1.fc11.x86_64.rpm
        rpm -Uvh glusterfs-core-3.2.4-1.fc11.x86_64.rpm
        rpm -Uvh glusterfs-fuse-3.2.4-1.fc11.x86_64.rpm

        """
        .stripIndent()

        then:
        assert gluster.getInstallScript() == expected

    }



    def "test getConfMaster" () {
        setup:
        def gluster = new GlusterFS()
        def expected = """\
        # Start the Gluster daemon
        service glusterd start

        # Create a volume and start it
        gluster volume create test-volume node:/somewhere
        gluster volume start test-volume

        # Assign the mounted to the current user
        chown -R pablo:wheel /somewhere
        """
        .stripIndent()

        when:
        gluster.volname = "test-volume"
        gluster.masterHostname = "node"
        gluster.brickPath = "/somewhere"
        gluster.path = "/here"
        gluster.userName = "pablo"

        then:
        assert gluster.getConfMaster() == expected

    }


    def "test getConfMaster with ebs vol" () {
        setup:
        def gluster = new GlusterFS()
        def expected = """\
        # Start the Gluster daemon
        service glusterd start

        # Create a volume and start it
        gluster volume create test-volume node:/mnt/sdh/brick1
        gluster volume start test-volume

        # Assign the mounted to the current user
        chown -R illo:wheel /mnt/sdh/brick1
        """
        .stripIndent()

        when:
        gluster.volname = "test-volume"
        gluster.masterHostname = "node"
        gluster.brickPath = "/brick1"
        gluster.path = "/here"
        gluster.device = "/dev/sdh"
        gluster.userName = "illo"
        gluster.blockStorageMountPath = "/mnt/sdh"

        then:
        assert gluster.getConfMaster() == expected

    }

    def "test getConfClient" () {

        setup:
        def gluster = new GlusterFS()
        def expected = """\
        modprobe fuse

        # Check and create mount path
        [ ! -e /here ] && mkdir -p /here
        [ -f /here ] && echo "The path to be mounted must be a directory. Make sure the following path is NOT a file: '/here'" && exit 1

        mount -t glusterfs node:/test-volume /here
        """
        .stripIndent()

        when:
        gluster.volname = "test-volume"
        gluster.masterHostname = "node"
        gluster.brickPath = "the-brick"
        gluster.path = "/here"

        then:
        assert gluster.getConfClient() == expected
    }


    def "test getBlockStorageMount with device" () {
        setup:
        def gluster = new GlusterFS( device: "/dev/sdh", blockStorageMountPath: "/mnt/sdh" )


        when:
        def expected = """\
        # Format the EBS volume if required
        mkfs.ext3 /dev/sdh; sleep 1

        # Mount the EBS volume if required
        [ ! -e /mnt/sdh ] && mkdir -p /mnt/sdh
        [ -f /mnt/sdh ] && echo "The path to be mounted must be a directory. Make sure the following path is NOT a file: '/mnt/sdh'" && exit 1
        mount /dev/sdh /mnt/sdh; sleep 1
        """ .stripIndent()

        then:
        gluster.getBlockStorageMount() == expected
    }


    def "test getBlockStorageMount (no device)" () {
        setup:
        def gluster = new GlusterFS( )

        expect:
        gluster.getBlockStorageMount() == ""
    }
}