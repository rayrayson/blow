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

package blow.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier

import java.security.PublicKey
import net.schmizz.sshj.connection.channel.direct.PTYMode

/**
 * Implement a terminal redirecting remote host i/o to the system current system i/o
 *
 * Author: Paolo Di Tommaso
 * Date: 4/23/12
 *
 */
class SshConsole {

    /** The remote host name to which connect */
    String host;

    /** The user name to use to connect to the remote host */
    String user = System.getProperty("user.name");

    /** The password to authenticate the user */
    String password

    /** The remote port to which connect (default: 22) */
    int port = 22;

    /** The path to the private key file */
    String key

    /** Whenever use compression for the connection */
    boolean useCompression = true;

    /** The terminal type to use e.g {@code ansi}, {@code vt100}, {@code vt220}, {@code vt320} */
    String tern = "vt100"


    private SSHClient ssh;
    private Session session;


    public void start() throws IOException {

        final jline.ConsoleReader console = new jline.ConsoleReaderInputStream()
        int rows = console.getTermheight();
        int cols = console.getTermwidth();

        ssh = new SSHClient();
        if( useCompression ) {
            ssh.useCompression();
        }


//        final File khFile = new File(OpenSSHKnownHosts.detectSSHDir(), "known_hosts");
//        ssh.addHostKeyVerifier(new ConsoleKnownHostsVerifier(khFile, System.console()));
//

        // don't bother verifying
        ssh.addHostKeyVerifier(
                new HostKeyVerifier() {
                    public boolean verify(String arg0, int arg1, PublicKey arg2) { true }
                }
        );


        ssh.connect( host );
        try {
            if( key ) {
                ssh.authPublickey(user,key);
            }
            else if( password ) {
                ssh.authPassword(user, password)
            }
            else {
                // try to use the user default public key
                ssh.authPublickey(user);
            }

            /*
             * start the session and request a remote terminal
             */
            session = ssh.startSession();
            session. allocatePTY(term, cols, rows, 0, 0, Collections.<PTYMode, Integer>emptyMap());

            final Session.Shell shell = session.startShell();

            /*
             * redirect the remote shell i/o to the current system console
             */
            new StreamPump(shell.getInputStream(), System.out) {  public boolean abort() { !shell.isOpen() }}
                    .bufSize(shell.getLocalMaxPacketSize())
                    .spawn("stdout")

            new StreamPump(shell.getErrorStream(), System.err) {  public boolean abort() { !shell.isOpen() }}
                    .bufSize(shell.getLocalMaxPacketSize())
                    .spawn("stderr");

            new StreamPump(System.in, shell.getOutputStream()) {  public boolean abort() { !shell.isOpen() }}
                    .bufSize(shell.getRemoteMaxPacketSize())
                    .spawn("stdin");


            /*
             * While the connection is established, change the remote shell size as
             * the console terminal resize
             */
            while( shell.isOpen() ) {

                int _rows = console.getTermheight();
                int _cols = console.getTermwidth();

                if( rows != _rows || cols != _cols ) {
                    rows = _rows; cols = _cols;
                    shell.changeWindowDimensions(cols, rows, 0, 0);
                }

                Thread.sleep(500);
            }
        }
        finally {
            if( session && session.isOpen() ) { session.close() }
            ssh.disconnect();
        }
    }

    /**
     * Only for testing purpose
     *
     * @param args
     */
    public static void main(String[] args) {

        String param = args.length > 0 ? args[0] :  "localhost";

        int p = param.indexOf('@');
        String host;
        String user = System.getProperty("user.name");
        if( p == -1 ) {
            host = param;
        }
        else {
            user = param.substring(0,p);
            host = param.substring(p+1);
        }

        println "Connecting to $host as $user"
        SshConsole term = new SshConsole(user: user, host: host)
        term.start()

    }

}