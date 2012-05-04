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

import org.apache.commons.io.FilenameUtils

/**
 *  Class providing command line helper
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CmdLineHelper {

    /**
     * Given a string the splitter method separate it by blank returning a list of string.
     * Tokens wrapped by a single quote or double quotes are considered as a contiguous string
     * and is added as a single element in the returned list.
     * <p>
     * For example the string: {@code "alpha beta 'delta gamma'"} will return the following result
     * {@code ["alpha", "beta", "delta gamma"]}
     *
     * @param cmdline The string to be splitted in single elements
     * @return A list of string on which each entry represent a command line argument, or an
     * empty list if the {@code cmdline} parameter is empty
     */
    static List<String> splitter( String cmdline ) {

        def result = new LinkedList<String>()

        if( cmdline ) {
            QuoteStringTokenizer tokenizer = new QuoteStringTokenizer(cmdline);
            while( tokenizer.hasNext() ) {
                result.add(tokenizer.next())
            }
        }

        result
    }

    static class FileTokens {

        /** The file name in a path */
        def String name

        /** The parent path as was in the original path */
        def String parentPath

        /** The parent path as {link @File} */
        def File parentFile

        def File getAbsoluteFile() {
            if( parentFile && name ) return new File(parentFile,name)
            if( parentFile ) return parentFile
            if( name ) return new File(name)
            else null
        }

    }

    /**
     * Given a string path return a {@link FileTokens} instance containing the path components
     *
     * @param filePath
     * @return
     */
    static def getFileTokens( String filePath ) {
        assert filePath != null

        def result = new FileTokens()

        def prefixFolder
        def prefixPath
        if( filePath.startsWith("~/") ) {
            filePath = filePath.substring(2)
            prefixFolder = new File( System.getProperty("user.home") )
            prefixPath = "~/"
        }

        if( filePath == "~" ) {
            result.name = "~"
            result.parentPath = ""
            result.parentFile = new File(".")
        }

        else {
            result.name = FilenameUtils.getName(filePath)
            result.parentPath = filePath.startsWith("~") ? FilenameUtils.getPath(filePath) : FilenameUtils.getFullPath(filePath)
            result.parentFile = result.parentPath ? new File(result.parentPath) : new File(".")
        }

        if( prefixPath ) {
            result.parentFile = new File(prefixFolder, result.parentPath)
            result.parentPath = prefixPath + result.parentPath
        }


        result
    }

}
