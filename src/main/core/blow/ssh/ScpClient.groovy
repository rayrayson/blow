/*
 * Copyright (c) 2012. Paolo Di Tommaso
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
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.xfer.FileSystemFile

import java.security.PublicKey

/**
 * Implements an SSH based SCP client
 * 
 * @author Paolo Di Tommaso
 *
 */
class ScpClient {

	String host
	
	int port = 22;
	
	/**
	 * Connection timeout (millis)
	 */
	int connectTimeout = 30000

	
	final private SSHClient ssh 
	
	/**
	 * Client constructor. To specify a remote host or override default ssh port 
	 * use the Groovy bean syntax. For example: 
	 * <pre>
	 * def client = new ScpClient( host: "remote.host.com", port: 2222 )
	 * </pre>
	 */
	ScpClient() {
		this.ssh = new SSHClient()
		this.ssh.useCompression();
		
		// don't bother verifying
		this.ssh.addHostKeyVerifier(
			new HostKeyVerifier() {
				public boolean verify(String arg0, int arg1, PublicKey arg2) { return true }
			}
		);

	}

	/**
	 * Connect to a remote ssh server 
	 * 
	 * @param host The remote host string or IP address
	 * @param port The remote host port 
	 * @param user The user name to authenticate with
	 * @param privateKey 
	 * 			The local file where the public key is stored,
	 * 			if not specified the default will be used ($HOME/.ssh/id_rsa | id_dsa)	
	 */
	def void connect( String host, int port, String user, File privateKey = null ) {
		
		ssh.connect(host,port)
		
		if( privateKey ) {
			ssh.authPublickey(user, privateKey.toString() )
		}
		else {
			ssh.authPublickey(user)
		}

	}
	
	def void connect( String host, String user, File privateKey = null ) {
		connect(host,port,user,privateKey)
	}
		 
	def void connect( String user, File privateKey = null ) {
		connect(host,port,user,privateKey)
	}
	
	/**
	 * Given a path split and normalize the 'directory' name and 'file' name. 
	 * <p>Non-absolute path are made relative to the user home "~". 
	 * For example: 
	 * <pre>
	 *  /some/path/file.txt --> ( /some/path, file.txt )
	 *  relative/path/file.txt --> ( ~/relative/path, file.txt )
	 *  ~/file.txt --> ( ~, file.txt ) 
	 * </pre>
	 * 
	 * @param remotePath The remote path string to parse 
	 * @return a map containing two entries with the following keys: <code>folderName</code> and <code>fileName</code>
	 */
	@Deprecated
	static private getTargetPaths( String remotePath, String defaultName = null ) {
	
		assert remotePath, "Parameter 'remotePath' cannot be empty"
		
		String targetFolder
		String targetFileName
		
		if( remotePath.endsWith("/") ) {
			targetFolder = remotePath.substring(0, remotePath.length()-1)
			targetFileName = defaultName
		}
		else  {
			def file = new File(remotePath)
			targetFolder = file.getParent()
			targetFileName = file.getName()
		}
		
		// if the targetHost file name is not provided (because targetPath specify only the directory
		// fallback on the local file name
		if( !targetFileName || targetFileName == '~' ) {
			targetFileName = defaultName
		}
		
		/*
		 * targetHost folder shoud be absolute, if not make it relative to the 'user home'
		 */
		if( !targetFolder ) {
			targetFolder = "~"
		}
		else if(  targetFolder != "~" && !targetFolder.startsWith("/") && !targetFolder.startsWith("~/") && !targetFolder.startsWith('$') ) {
			targetFolder = "~/" + targetFolder
		}
		
		return [ folderName: targetFolder, fileName: targetFileName ]
	}
	
	static String normalizeRemotePath( String remotePath ) {
	
		if( remotePath == "~" || remotePath == "~/" ) {
			return "."	
		}
		if( remotePath.startsWith("~/") ) {
			return "./" + remotePath.substring(2)	
		}
		else if( !remotePath.startsWith("/") && !remotePath.startsWith("./") ) {
			return "./" + remotePath
		} 
		
		return remotePath
	}
	

	def void uploadString( String str, String remotePath  ) {
	
		assert str, "Argument 'str' cannot be null"
		assert ssh?.isConnected(), "Cannot upload without a connection. Did you connect() before this?"
		
		ssh.newSCPFileTransfer().upload( new InMemoryPayload(str), normalizeRemotePath(remotePath) )
	}
	
	def void uploadData( byte[] data, String remotePath  )  {

		assert data, "Argument 'data' cannot be null"
		assert ssh?.isConnected(), "Cannot upload without a connection. Did you connect() before this?"

		ssh.newSCPFileTransfer() .upload( new InMemoryPayload(data), normalizeRemotePath(remotePath) )
	}
	
	def void uploadFile( String localPath, String remotePath )  {
		uploadFile(new File(localPath), remotePath )
	}
	
	def void uploadFile( File localPath, String remotePath )  {
		
		assert localPath.exists(), "The specified file path does not exist: ${localPath}"
		assert localPath.isFile(), "The specified path is not a file: ${localPath}" 
		assert ssh?.isConnected(), "Cannot upload without a connection. Did you connect() before this?" 
		
		ssh.newSCPFileTransfer().upload( new FileSystemFile(localPath), normalizeRemotePath(remotePath) )		

	}
	
	
	def void download( String remotePath, String localPath = null )  {
		assert ssh?.isConnected(), "Cannot upload without a connection. Did you connect() before this?"
		
		if( !localPath ) {
			def remoteFile = new File(remotePath)
			localPath = "./${remoteFile.getName()}"
		}

		ssh.newSCPFileTransfer().download(remotePath, localPath)
	}
	
	def void close() {
		ssh.disconnect()
	} 
	
}
