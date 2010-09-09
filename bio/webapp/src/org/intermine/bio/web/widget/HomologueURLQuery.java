package org.intermine.bio.web.widget;

/*
 * Copyright (C) 2002-2010 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import org.intermine.api.profile.InterMineBag;
import org.intermine.objectstore.ObjectStore;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.OrderElement;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.logic.widget.WidgetURLQuery;

/**
 * Builds a pathquery.  Used when a user clicks on a results record in an enrichment widget.
 * @author Julie Sullivan
 */

public class HomologueURLQuery implements WidgetURLQuery
{

    private InterMineBag bag;
    private String key;
    private ObjectStore os;

    /**
     * @param key value selected by user to display
     * @param bag bag included in query
     * @param os object store
     */
    public HomologueURLQuery(ObjectStore os, InterMineBag bag, String key) {
        this.bag = bag;
        this.key = key;
        this.os = os;
    }

    /**
     * {@inheritDoc}
     */
    public PathQuery generatePathQuery(boolean showAll) {
        PathQuery q = new PathQuery(os.getModel());
        q.addViews("Gene.primaryIdentifier", "Gene.symbol", "Gene.organism.name",
                "Gene.homologues.homologue.primaryIdentifier",
                "Gene.homologues.homologue.symbol",
                "Gene.homologues.homologue.organism.name",
                "Gene.homologues.type");
        q.addConstraint(Constraints.in(bag.getType(), bag.getName()));
        q.addConstraint(Constraints.eq("Gene.homologues.type", "orthologue"));
        if (!showAll) {
            q.addConstraint(Constraints.lookup("Gene.homologues.homologue.organism", key, ""));
        }
        q.addOrderBy(new OrderElement("Gene.organism.name", OrderDirection.ASC));
        q.addOrderBy(new OrderElement("Gene.primaryIdentifier", OrderDirection.ASC));
        q.addOrderBy(new OrderElement("Gene.homologues.homologue.organism.name",
                OrderDirection.ASC));
        q.addOrderBy(new OrderElement("Gene.homologues.homologue.primaryIdentifier",
                OrderDirection.ASC));

        return q;
    }
}

