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
class GlusterFSOpTest extends Specification {

    def "test nodeName" () {

        expect:
        GlusterFSOp.nodeName("node1:/some/path") == 'node1'

    }

    def "test nodePath" () {
        expect:
        GlusterFSOp.nodePath("node1:/some/path") == '/some/path'

    }

    def "test getInstallScript" () {
        when:
        def gluster = new GlusterFSOp()
        def expected = """\
        # Install required dependencies
        blowpkg -y install fuse fuse-libs

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


    def "test getConfClient" () {

        setup:
        def gluster = new GlusterFSOp()
        def expected = """\
        modprobe fuse

        # Check and create mount path
        [ ! -e /here ] && mkdir -p /here
        [ -f /here ] && echo "The path to be mounted must be a directory. Make sure the following path is NOT a file: '/here'" && exit 1

        mount -t glusterfs node:/test-volume /here
        """
        .stripIndent()

        when:
        gluster.volumeName = "test-volume"
        gluster.serverName = "node"
        gluster.path = "/here"

        then:
        assert gluster.getConfClient() == expected
    }





}