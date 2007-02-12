/*
 * (c) Copyright 2004, 2005, 2006, 2007 Hewlett-Packard Development Company, LP
 * [See end of file]
 */

package com.hp.hpl.jena.query.engine.binding;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.*;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.core.Var;

/**
 * @author     Andy Seaborne
 * @version    $Id: QueryEngineUtils.java,v 1.14 2007/02/06 17:06:06 andy_seaborne Exp $
 */
 
public class BindingUtils
{
    private static Log log = LogFactory.getLog(BindingUtils.class) ;
  
    public static Triple substituteIntoTriple(Triple t, Binding binding)
    {
        Node subject = substituteNode(t.getSubject(), binding) ;
        Node predicate = substituteNode(t.getPredicate(), binding) ;
        Node object = substituteNode(t.getObject(), binding) ;
        
        if ( subject == t.getSubject() &&
             predicate == t.getPredicate() &&
             object == t.getObject() )
             return t ;
             
        return new Triple(subject, predicate, object) ;
    }
    
    public static Node substituteNode(Node n, Binding binding)
    {
        if ( ! n.isVariable() )
            return n ;

        if ( ! (n instanceof Var) )
            log.fatal("Node_Variable, not a Var") ; 
        
        //String name = ((Node_Variable)n).getName() ;
        Var var = Var.alloc(n) ;
        Object obj = null ;
        
        if ( binding != null )
            obj = binding.get(var) ;
        
        if ( obj == null )
            return n ;
            
        if ( obj instanceof Node )
            return (Node)obj ;

        LogFactory.getLog(BindingUtils.class).warn("Unknown object in binding: ignored: "+obj.getClass().getName()) ;        
        return n ;
    }
    
    
    public static Binding asBinding(QuerySolution qSolution)
    {
        Binding binding = new BindingMap(null) ;
        addToBinding(binding, qSolution) ;
        return binding ;
    }
        
    public static void addToBinding(Binding binding, QuerySolution qSolution)
    {        
        for ( Iterator iter = qSolution.varNames() ; iter.hasNext() ; )
        {
            String n = (String)iter.next() ;
            RDFNode x = qSolution.get(n) ;
            binding.add(Var.alloc(n), x.asNode()) ;
        }
    }
}

/*
 *  (c) Copyright 2004, 2005, 2006, 2007 Hewlett-Packard Development Company, LP
 *  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
