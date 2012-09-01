/*
 * copyright (c) 2012, the authors.
 *
 *   this file is part of blow.
 *
 *   blow is free software: you can redistribute it and/or modify
 *   it under the terms of the gnu general public license as published by
 *   the free software foundation, either version 3 of the license, or
 *   (at your option) any later version.
 *
 *   blow is distributed in the hope that it will be useful,
 *   but without any warranty; without even the implied warranty of
 *   merchantability or fitness for a particular purpose.  see the
 *   gnu general public license for more details.
 *
 *   you should have received a copy of the gnu general public license
 *   along with blow.  if not, see <http://www.gnu.org/licenses/>.
 */

package blow.builder

import org.codehaus.groovy.runtime.InvokerHelper

/**
 * An extended version of Groovy {@link BuilderSupport}.
 * <p>
 * The main difference it that {@link BuilderExtended} is able to handle
 * a list of values as parameter other than map and single value object.
 * For example:
 *
 * <code>
 * conf {
 *
 *     elem1 'a', 'b', 'c'
 *
 *     elem2 'alpha', 'beta', 'delta', x:1, y: 2
 *
 *     elem3 (1,2,3) {
 *         subelem
 *     }
 *
 * }
 * </code>
 *
 *
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 *  @author <a href="mailto:james@coredevelopers.net">James Strachan</a>
 */
public abstract class BuilderExtended extends GroovyObjectSupport {

    private Object current;
    private Closure nameMappingClosure;
    private final BuilderExtended proxyBuilder;

    public BuilderExtended() {
        this.proxyBuilder = this;
    }

    public BuilderExtended(BuilderExtended proxyBuilder) {
        this(null, proxyBuilder);
    }

    public BuilderExtended(Closure nameMappingClosure, BuilderExtended proxyBuilder) {
        this.nameMappingClosure = nameMappingClosure;
        this.proxyBuilder = proxyBuilder;
    }

    /**
     * Convenience method when no arguments are required
     *
     * @param methodName the name of the method to invoke
     * @return the result of the call
     */
    public Object invokeMethod(String methodName) {
        return invokeMethod(methodName, null);
    }

    public Object invokeMethod(String methodName, Object args) {
        Object name = getName(methodName);
        return doInvokeMethod(methodName, name, args);
    }

    protected Object doInvokeMethod(String methodName, Object name, Object args) {
        Object node = null;
        Closure closure = null;
        List list = InvokerHelper.asList(args);

        //System.out.println("Called invokeMethod with name: " + name + " arguments: " + list);

        switch (list.size()) {
            case 0:
                node = proxyBuilder.createNode(name);
                break;
            case 1:
                Object object = list.get(0);
                if (object instanceof Map) {
                    node = proxyBuilder.createNode(name, (Map) object);
                } else if (object instanceof Closure) {
                    closure = (Closure) object;
                    node = proxyBuilder.createNode(name);
                } else {
                    node = proxyBuilder.createNode(name, object);
                }
                break;


            default:
                if( list.last() instanceof  Closure ) {
                    closure = (Closure) list.last()
                    list = list.subList(0,list.size()-1)
                }

                if( list[0] instanceof Map ) {
                    Map arg0 = list[0]
                    list = list.subList(1,list.size())
                    if( list.size() == 0 ) {
                        node = proxyBuilder.createNode(name, (Map) arg0)
                    }
                    else if( list.size() == 1 ) {
                        node = proxyBuilder.createNode(name, (Map) arg0, list[0])
                    }
                    else {
                        node = proxyBuilder.createNode(name, (Map) arg0, list)
                    }
                }
                else if( list.size() == 1 ) {
                    node = proxyBuilder.createNode(name, list[0] )
                }
                else if( list.size() == 2 && list[1] instanceof Map ) {
                    node = proxyBuilder.createNode(name, (Map) list[1], list[0] )
                }
                else {
                    node = proxyBuilder.createNode(name, list)
                }



        }

        if (current != null) {
            proxyBuilder.setParent(current, node);
        }

        if (closure != null) {
            // push new node on stack
            Object oldCurrent = getCurrent();
            setCurrent(node);
            // let's register the builder as the delegate
            setClosureDelegate(closure, node);
            closure.call();
            setCurrent(oldCurrent);
        }

        proxyBuilder.nodeCompleted(current, node);
        return proxyBuilder.postNodeCompletion(current, node);
    }

    /**
     * A strategy method to allow derived builders to use
     * builder-trees and switch in different kinds of builders.
     * This method should call the setDelegate() method on the closure
     * which by default passes in this but if node is-a builder
     * we could pass that in instead (or do something wacky too)
     *
     * @param closure the closure on which to call setDelegate()
     * @param node    the node value that we've just created, which could be
     *                a builder
     */
    protected void setClosureDelegate(Closure closure, Object node) {
        closure.setDelegate(this);
    }

    protected abstract void setParent(Object parent, Object child);

    protected abstract Object createNode(Object name);

    protected abstract Object createNode(Object name, Object value);

    protected abstract Object createNode(Object name, Map attributes);

    protected abstract Object createNode(Object name, Map attributes, Object value);

    /**
     * A hook to allow names to be converted into some other object
     * such as a QName in XML or ObjectName in JMX.
     *
     * @param methodName the name of the desired method
     * @return the object representing the name
     */
    protected Object getName(String methodName) {
        if (nameMappingClosure != null) {
            return nameMappingClosure.call(methodName);
        }
        return methodName;
    }


    /**
     * A hook to allow nodes to be processed once they have had all of their
     * children applied.
     *
     * @param node   the current node being processed
     * @param parent the parent of the node being processed
     */
    protected void nodeCompleted(Object parent, Object node) {
    }

    /**
     * A hook to allow nodes to be processed once they have had all of their
     * children applied and allows the actual node object that represents
     * the Markup element to be changed
     *
     * @param node   the current node being processed
     * @param parent the parent of the node being processed
     * @return the node, possibly new, that represents the markup element
     */
    protected Object postNodeCompletion(Object parent, Object node) {
        return node;
    }

    protected Object getCurrent() {
        return current;
    }

    protected void setCurrent(Object current) {
        this.current = current;
    }

}