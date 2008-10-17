/*
 * (c) Copyright 2008 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package dev.opt;

import java.util.Map;
import java.util.Set;

import com.hp.hpl.jena.sparql.algebra.Op;
import com.hp.hpl.jena.sparql.algebra.TransformCopy;
import com.hp.hpl.jena.sparql.algebra.Transformer;
import com.hp.hpl.jena.sparql.algebra.op.OpBGP;
import com.hp.hpl.jena.sparql.algebra.op.OpFilter;
import com.hp.hpl.jena.sparql.algebra.opt.TransformFilterPlacement;
import com.hp.hpl.jena.sparql.core.BasicPattern;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.expr.ExprList;
import com.hp.hpl.jena.tdb.solver.reorder.ReorderFixed;
import com.hp.hpl.jena.tdb.solver.reorder.ReorderProc;
import com.hp.hpl.jena.tdb.solver.reorder.ReorderTransformation;

public class Reorganise
{
    // At the moment, we can do all reorganisation as a bottom-up walk. 
    //    BGP gets reordered,
    //    Then filters flowed into position.
    // If we want to take a more holistic view, need to do the rewriting as a top down analysis
    
    
    // Use OpWalker as a before visitor walker?  
    //  Need to intercept/modify OpWalker.WalkerVisitor
    // Can use OpWalker.WalkerVisitor for that.

    public static Op reorganise(Op op, Map<Op, Set<Var>> x)
    {
        return Transformer.transform(new ReorganiseTransform(x), op) ;
    }
    
    private static final class ReorganiseTransform extends TransformCopy
    {
        Map<Op, Set<Var>> scopeMap ;
        
        public ReorganiseTransform(Map<Op, Set<Var>> scopeMap)
        {
            super() ;
            this.scopeMap = scopeMap ;
        }

        // Places we want to intercept the walk.
        @Override
        public Op transform(OpFilter opFilter, Op sub)
        {
            if ( OpBGP.isBGP(sub) )
            {
                return reorganise((OpBGP)sub, opFilter.getExprs(), scopeMap.get(opFilter)) ;
            }
            return super.transform(opFilter, sub) ;
        }

        @Override
        public Op transform(OpBGP opBGP)
        {
            return reorganise(opBGP, null, scopeMap.get(opBGP)) ;
        }
        
        private Op reorganise(OpBGP opBGP, ExprList exprs, Set<Var> set)
        {
            BasicPattern pattern = opBGP.getPattern() ;
            ReorderTransformation transform = new ReorderFixed() ;
            
//            ReorderTransformation transform = new ReorderTransformationBase(){
//                @Override
//                protected double weight(PatternTriple pt)
//                {
//                    return 0 ;
//                }} ;

            ReorderProc proc = transform.reorderIndexes(pattern) ;
            pattern = proc.reorder(pattern) ; 

            Op op = null ;
            if ( exprs != null )
                op = TransformFilterPlacement.transform(exprs, pattern) ;
            else
                op = new OpBGP(pattern) ;
            
            return op ;
        }
    }
    
}

/*
 * (c) Copyright 2008 Hewlett-Packard Development Company, LP
 * All rights reserved.
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