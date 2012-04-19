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

package blow.scp

import org.junit.Test

import blow.scp.ScpClient;

class ScpClientTest {

	@Test
	public void testNormalizeRemotePath() {

		assert "/some/file.txt" ==  ScpClient.normalizeRemotePath( "/some/file.txt" )
		assert "./some/file.txt" ==  ScpClient.normalizeRemotePath( "some/file.txt" )
		assert "./some/file.txt" ==  ScpClient.normalizeRemotePath( "./some/file.txt" )
		assert "./some/file.txt" ==  ScpClient.normalizeRemotePath( "~/some/file.txt" )
		assert "." ==  ScpClient.normalizeRemotePath( "~/" )
		assert "./~file.txt" ==  ScpClient.normalizeRemotePath( "~file.txt" )
		assert "./.hidden.txt" ==  ScpClient.normalizeRemotePath( ".hidden.txt" )
	} 
	
	@Test 
	public void testTargetNames() {
	
		def target = ScpClient.getTargetPaths( "/some/file.txt" )	
		assert "/some" == target.folderName
		assert "file.txt" == target.fileName

		target = ScpClient.getTargetPaths( "file.txt" )
		assert "~" == target.folderName
		assert "file.txt" == target.fileName
		
		target = ScpClient.getTargetPaths( "~/file.txt" )
		assert "~" == target.folderName
		assert "file.txt" == target.fileName

		target = ScpClient.getTargetPaths( "~", "file.txt" )
		assert "~" == target.folderName
		assert "file.txt" == target.fileName

		
		target = ScpClient.getTargetPaths( "~/some/path/", "file.txt" )
		assert "~/some/path" == target.folderName
		assert "file.txt" == target.fileName

		target = ScpClient.getTargetPaths( "~/some/path/the_filename.txt", "file.txt" )
		assert "~/some/path" == target.folderName
		assert "the_filename.txt" == target.fileName


		target = ScpClient.getTargetPaths( "/some/abs/path/the_filename.txt", "file.txt" )
		assert "/some/abs/path" == target.folderName
		assert "the_filename.txt" == target.fileName
		
		target = ScpClient.getTargetPaths( "/some/abs/path/", "file.txt" )
		assert "/some/abs/path" == target.folderName
		assert "file.txt" == target.fileName
	} 
	
	@Test
	public void testConnect() {
	
		ScpClient client = new ScpClient( host:"palestine.crg.es" )	
		
		String idFile = System.properties.getProperty("user.home") + "/.ssh/id_rsa"
		def file  = new File(idFile)
		
		assert file.exists()
		
		client.connect("ptommaso", file )
	} 

	@Test
	public void testUploadFile() {
		
		ScpClient client = new ScpClient( host:"palestine.crg.es")
		client.connect("ptommaso")
		client.uploadFile( "./build.groovy", "/users/cn/ptommaso/Downloads/scp_test_9.txt" )
	} 
	
	@Test 
	public void testUploadString( ) {

		ScpClient client = new ScpClient( host:"palestine.crg.es")
		client.connect("ptommaso")
		client.uploadString( "Hello world!", "./Downloads/scp_rel_string.txt" )
	}

	@Test
	public void testUploadHome( ) {

		ScpClient client = new ScpClient( host:"palestine.crg.es")
		client.connect("ptommaso")
		client.uploadString( "Hello world!", "~/Downloads/scp_home_string.txt" )
		
	}
		
	@Test
	public void testDownload() {
		
		def target = new File("./s3_upload")
		if( target.exists() ) target.delete()
		
		ScpClient client = new ScpClient( host:"palestine.crg.es" )
		client.connect("ptommaso")
		
		client.download("./s3_upload" )
		
		assert ( target .exists())
	} 
}
