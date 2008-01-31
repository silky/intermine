package org.intermine.web.logic.widget;

/*
 * Copyright (C) 2002-2007 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import org.apache.log4j.Logger;

/**
 * Calculates p-values for go terms using the hypergeometric distribution.
 * See online documentation for detailed information about what this class is and what it does.
 */
public class Hypergeometric
{
    static double[] factorials;
    private static final Logger LOG = Logger.getLogger(Hypergeometric.class);
    /**
     * Builds an array of factorials so we don't have to calculate it each time.
     * @param numGenes the number of genes in the list
     **/
    public Hypergeometric(int numGenes) {
        factorials = new double[numGenes + 1];
        factorials[0] = 0;
        double current = 0;
        for (int i = 1; i < numGenes + 1; i++) {
            current += Math.log(i);
            factorials[i] = current;
        }
    }


    /**
     * Compute the log of nCr (n Choose r)
     *           n!
     * nCr =  ---------
     *        r! (n-r)!
     * @param n 
     * @param r 
     * @return double the log of nCr
     */
    private static double logChoose(int n, int r) {
        if (n == 0) {
            if (r == 0) {
                return 0;
            } else {
                return Double.NEGATIVE_INFINITY;
            }
        }
        if (r == 0) {
            return 0;
        }
        if (r == 1) {
            return Math.log(n);
        }
        if (n < r) {
            return Double.NEGATIVE_INFINITY;            
        }
        return factorials[n] - (factorials[r] + factorials[n - r]);
    }


    /**
     * The value is calculated as:
     *
     *      (M choose x) (N-M choose n-x)
     * P =   -----------------------------
     *               N choose n
     * 
     * @param n total number of genes in our list
     * @param k number of genes in our list annotated with this term     
     * @param bigN Total number of genes in the database
     * @param bigM Total number of genes in the database annotated with this term 
     * @return p-value for this go term
     **/
    public static double calculateP(int n, int k, int bigM, int bigN) {
        double p = 0;
        for (int i = n; i >= k; i--) {
            p +=
                Math.exp(logChoose(bigM, i) + logChoose(bigN - bigM, n - i) - logChoose(bigN, n));
        }
        LOG.error("n = " + n + " k = " + k + " N = " + bigN + " M " + bigM);
        return p;
    }
}
