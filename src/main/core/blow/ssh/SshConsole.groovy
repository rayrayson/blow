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

package blow.ssh

import groovy.util.logging.Slf4j
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.ChannelOutputStream
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.fusesource.jansi.AnsiConsole
import sun.misc.Signal
import sun.misc.SignalHandler

import java.nio.channels.Channels
import java.security.PublicKey

/**
 * Implement a terminal redirecting remote host i/o to the system current system i/o
 *
 * @author Paolo Di Tommaso
 * Date: 4/23/12
 *
 */
@Slf4j
class SshConsole {

    static final boolean isWindowsOS = System.getProperty("os.name")?.startsWith("Windows")

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
    String term = "vt100"


    private SSHClient ssh;
    private Session session;

    private jline.ConsoleReader console
    private int rows
    private int cols

    public void launch() throws IOException {

        console = new jline.ConsoleReader();
        rows = console.getTermheight();
        cols = console.getTermwidth();

        ssh = new SSHClient();
        if( useCompression ) {
            ssh.useCompression();
        }


//        final File khFile = new File(OpenSSHKnownHosts.detectSSHDir(), "known_hostname");
//        ssh.addHostKeyVerifier(new ConsoleKnownHostsVerifier(khFile, System.console()));
//

        // don't bother verifying
        ssh.addHostKeyVerifier(
                new HostKeyVerifier() {
                    public boolean verify(String arg0, int arg1, PublicKey arg2) { true }
                }
        );


        def Thread keyboardListener
        def prevSigStop
        def prevSigInt

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
             * installs signals handles
             */
            prevSigInt = installSignalHandlerForCtrl_C(shell)
            prevSigStop = installSignalHandlerForCtrl_Z(shell)

            /*
             * redirect the remote shell i/o to the current system console
             */
            new StreamPump(shell.getInputStream(), AnsiConsole.out) {  public boolean abort() { !shell.isOpen() }}
                    .bufSize(shell.getLocalMaxPacketSize())
                    .spawn("stdout")

            new StreamPump(shell.getErrorStream(), AnsiConsole.err) {  public boolean abort() { !shell.isOpen() }}
                    .bufSize(shell.getLocalMaxPacketSize())
                    .spawn("stderr");

            // provides the console input (keyboard) to the shell
            // note: it uses a custom loop instead of the stream pump because the latter
            // was blocking on the read method and so the underlying thread won't terminate
            // as the user logout from the remote shell

            def reader = isWindowsOS ? this.&consoleReaderForWindows : this.&consoleReaderForLinux

            keyboardListener = new Thread() {
                public void run() {

                    try {
                        reader(shell)
                    }
                    catch( InterruptedIOException e ) {
                        log.debug "SSH Console reader InterruptedIOException"
                    }
                    catch( InterruptedException e ) {
                        log.debug "SSH Console reader InterruptedException"
                    }
                    catch( Throwable e ) {
                        log.debug("SSH Console reader exception",e)
                    }
                }
            }
            .start()

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

                Thread.sleep(800);
            }

        }
        finally {
            try { keyboardListener.interrupt() } catch( Exception e ) {}
            try { if( session && session.isOpen() ) { session.close() } } catch( Exception e ) {}
            try { ssh.disconnect(); } catch( Exception e ) { }

            if( prevSigStop ) Signal.handle(new Signal("TSTP"), prevSigStop )
            if( prevSigInt ) Signal.handle(new Signal("INT"), prevSigInt )
        }
    }

    private installSignalHandlerForCtrl_Z(def shell) {

        if( isWindowsOS ) {
            log.trace "Skipping ctrl+z signal handler on Windows"
            return
        }

        try {
            sun.misc.Signal.handle(new Signal("TSTP"), new SignalHandler() { @Override void handle(Signal signal) {
                shell.getOutputStream().write(0x1A)   // send CTRL+Z signal
                shell.getOutputStream().flush()

            }} )
        }
        catch( Exception e ) {
            log.warn("Cannot install term signal handler 'TSTP'", e)
        }

    }

    private void consoleReaderForWindows( Session.Shell shell ) {
        OutputStream _out = shell.getOutputStream()
        InputStream _in = Channels.newInputStream((new FileInputStream(FileDescriptor.in)).getChannel())

        // define a buffer in a safe way
        int maxPacketSize = shell.getRemoteMaxPacketSize()
        log.debug("Ptty remoteMaxPacketSize: $maxPacketSize")
        byte[] buf = new byte[maxPacketSize];

        // read the input from the keyboard and sent to the ptty
        while( shell.isOpen() ) {
            int ch = _in.read()
            if( ch == -1 ) {
                log.debug("Keyboard listener terminated")
                break
            }

            // Hack!! Java returns 0xA (line feed) pressing the enter key, but some terminal application
            // does not work properly. It seems better to replace it with 0xD (carriage return)
            if( ch ==0xA ) { ch=0xD }

            _out.write(ch)
            if( !_in.available() ) {
                _out.flush()
            }
        }

    }

    private void consoleReaderForLinux( Session.Shell shell ) {
        ChannelOutputStream _out = shell.getOutputStream()
        InputStream _in = System.in

        // define a buffer in a safe way
        int maxPacketSize = shell.getRemoteMaxPacketSize()
        log.debug("Ptty remoteMaxPacketSize: $maxPacketSize")

        // read input from the keyboard and sent to the ptty
        while( !_out.closed && shell.isOpen() ) {
            if( _in.available() <= 0 ) {
                Thread.sleep(40)
                continue
            }

            int ch = _in.read()
            if( ch == -1 ) {
                log.debug("Keyboard listener terminated")
                break
            }

            // Hack!! Java returns 0xA (line feed) pressing the enter key, but some terminal application
            // does not work properly. It seems better to replace it with 0xD (carriage return)
            if( ch ==0xA ) { ch=0xD }

            _out.write(ch)
            if( _in.available() <= 0 ) {
                _out.flush()

                /*
                * resize the terminal if required
                */
                int _rows = console.getTermheight();
                int _cols = console.getTermwidth();

                if( rows != _rows || cols != _cols ) {
                    rows = _rows; cols = _cols;
                    shell.changeWindowDimensions(cols, rows, 0, 0);
                }

            }
        }

    }

    private installSignalHandlerForCtrl_C(def shell) {
        try {
             Signal.handle(new Signal("INT"), new SignalHandler() { @Override void handle(Signal signal) {
                shell.getOutputStream().write(3)    // send CTRL+C signal
                shell.getOutputStream().flush()
            }} )
        }
        catch( Exception e ) {
            log.warn ("Cannot install term signal handler 'INT'", e)
        }
    }

    /**
     * Only for testing purpose
     *
     * @param args
     */
    public static void main(String[] args) {

        String param = args.length > 0 ? args[0] :  "localhost";
        String key = System.getProperty("user.home") + "/.ssh/id_rsa"

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
        SshConsole term = new SshConsole(user: user, host: host, key: key)
        term.launch()
    }

}