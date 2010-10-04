package org.intermine.bio.postprocess;

/*
 * Copyright (C) 2002-2010 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.bio.util.BioQueries;
import org.intermine.model.bio.Chromosome;
import org.intermine.model.bio.DataSet;
import org.intermine.model.bio.DataSource;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.GeneFlankingRegion;
import org.intermine.model.bio.Location;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.util.DynamicUtil;

/**
 * Create features to represent flanking regions of configurable distance either side of gene
 * featues.  These will be used in overlap queries.
 * @author rns
 *
 */
public class CreateFlankingRegions
{
    private ObjectStoreWriter osw = null;
    private ObjectStore os;
    private DataSet dataSet;
    private DataSource dataSource;
    private Map<Integer, Chromosome> chrs = new HashMap<Integer, Chromosome>();

    /**
     * The sizes in kb of flanking regions to create.
     */
    private static double[] distances = new double[] {0.5, 1, 2, 5, 10};

    /**
     * The values strings for up/down stream from a gene.
     */
    private static String[] directions = new String[] {"upstream", "downstream"};



    private static final Logger LOG = Logger.getLogger(CreateFlankingRegions.class);

    /**
     * Create a new CreateFlankingRegions object that will operate on the given
     * ObjectStoreWriter.
     *
     * @param osw the ObjectStoreWriter to use when creating/changing objects
     */
    public CreateFlankingRegions(ObjectStoreWriter osw) {
        this.osw = osw;
        this.os = osw.getObjectStore();
        dataSource = (DataSource) DynamicUtil.createObject(Collections.singleton(DataSource.class));
        dataSource.setName("modMine");
        try {
            dataSource = (DataSource) os.getObjectByExample(dataSource,
                    Collections.singleton("name"));
        } catch (ObjectStoreException e) {
            throw new RuntimeException(
                    "unable to fetch modMine DataSource object", e);
        }
    }

    /**
     * Iterate over genes in database and create flanking regions.
     *
     * @throws ObjectStoreException
     *             if there is an ObjectStore problem
     */
    public void createFlankingFeatures() throws ObjectStoreException {
        Results results = BioQueries.findLocationAndObjects(os,
                Chromosome.class, Gene.class, false, false, false, 1000);

        dataSet = (DataSet) DynamicUtil.createObject(Collections
                .singleton(DataSet.class));
        dataSet.setName("modMine gene flanking regions");
        dataSet.setDescription("Gene flanking regions generated by modMine");
        dataSet.setVersion("" + new Date()); // current time and date
        dataSet.setUrl("http://intermine.modencode.org");
        dataSet.setDataSource(dataSource);

        Iterator<?> resIter = results.iterator();

        int count = 0;

        osw.beginTransaction();
        while (resIter.hasNext()) {
            ResultsRow<?> rr = (ResultsRow<?>) resIter.next();
            Integer chrId = (Integer) rr.get(0);
            Gene gene = (Gene) rr.get(1);
            Location loc = (Location) rr.get(2);
            createAndStoreFlankingRegion(getChromosome(chrId), loc, gene);
            if ((count % 1000) == 0) {
                LOG.info("Created flanking regions for " + count + " genes.");
            }
            count++;
        }
        osw.store(dataSet);

        osw.commitTransaction();
    }


    private void createAndStoreFlankingRegion(Chromosome chr, Location geneLoc, Gene gene)
        throws ObjectStoreException {
        // This code can't cope with chromosomes that don't have a length
        if (chr.getLength() == null) {
            LOG.warn("Attempted to create GeneFlankingRegions on a chromosome without a length: "
                    + chr.getPrimaryIdentifier());
            return;
        }

        // if there is no gene symbol this is a new gene generated by gene models modENCODE
        // submissions.  This should work by DataSet but WormBase chado isn't setting a DataSet
        // correctly
        if (StringUtils.isEmpty(gene.getSymbol())) {
            return;
        }

        for (double distance : distances) {
            for (String direction : directions) {
                String strand = geneLoc.getStrand();

                // TODO what do we do if strand not set?
                int geneStart = geneLoc.getStart().intValue();
                int geneEnd = geneLoc.getEnd().intValue();
                int chrLength = chr.getLength().intValue();

                // gene touches a chromosome end so there isn't a flanking region
                if ((geneStart <= 1) || (geneEnd >= chrLength)) {
                    continue;
                }

                GeneFlankingRegion region = (GeneFlankingRegion) DynamicUtil
                .createObject(Collections.singleton(GeneFlankingRegion.class));
                Location location = (Location) DynamicUtil
                .createObject(Collections.singleton(Location.class));

                region.setDistance("" + distance + "kb");
                region.setDirection(direction);
                region.setGene(gene);
                region.setChromosome(chr);
                region.setChromosomeLocation(location);
                region.setOrganism(gene.getOrganism());
                region.setPrimaryIdentifier(gene.getPrimaryIdentifier() + " " + distance + "kb "
                        + direction);

                // this should be some clever algorithm
                int start, end;

                if ("upstream".equals(direction) && "1".equals(strand)) {
                    start = geneStart - (int) Math.round(distance * 1000);
                    end = geneStart - 1;
                } else if ("upstream".equals(direction) && "-1".equals(strand)) {
                    start = geneEnd + 1;
                    end = geneEnd + (int) Math.round(distance * 1000);
                } else if ("downstream".equals(direction) && "1".equals(strand)) {
                    start = geneEnd + 1;
                    end = geneEnd + (int) Math.round(distance * 1000);
                } else {  // direction.equals("downstream") && strand.equals("-1")
                    start = geneStart - (int) Math.round(distance * 1000);
                    end = geneStart - 1;
                }

                // if the region hangs off the start or end of a chromosome set it to finish
                // at the end of the chromosome
                location.setStart(new Integer(Math.max(start, 1)));
                int e = Math.min(end, chr.getLength().intValue());
                location.setEnd(new Integer(e));

                location.setStrand(strand);
                location.setLocatedOn(chr);
                location.setFeature(region);

                region.setLength(new Integer((location.getEnd().intValue()
                        - location.getStart().intValue()) + 1));

                osw.store(location);
                osw.store(region);
            }
        }
    }

    private Chromosome getChromosome(Integer chrId) throws ObjectStoreException {
        Chromosome chr = chrs.get(chrId);
        if (chr == null) {
            chr = (Chromosome) os.getObjectById(chrId, Chromosome.class);
            chrs.put(chrId, chr);
        }
        return chr;
    }
}
