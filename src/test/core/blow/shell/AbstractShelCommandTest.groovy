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

package blow.shell;

import spock.lang.Specification;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class AbstractShelCommandTest extends Specification {


//    def "test type" () {
//        when:
//        def s0 = new AbstractShellCommand(){
//            @Override
//            void invoke() {
//            }}
//
//
//        def s1 = new AbstractShellCommand<String>(){
//            @Override
//            void invoke() {
//            }}
//
//        def s2 = new AbstractShellCommand<ParamsHolder>() {
//            @Override
//            void invoke() {
//            }}
//
//        def s3 = new AbstractShellCommand<List<String>>() {
//            @Override
//            void invoke() {
//            }}
//
//
//        then:
//        s0.getParamsClass() == Object
//        s1.getParamsClass() == String
//        s2.getParamsClass() == ParamsHolder
//        s3.getParamsClass() == List
//
//    }
//
//
//
}


class ParamsHolder {
    String a
    String b
    String c
}