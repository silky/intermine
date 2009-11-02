package org.intermine.web.task;

/*
 * Copyright (C) 2002-2009 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.FileReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.intermine.api.profile.Profile;
import org.intermine.api.profile.ProfileManager;
import org.intermine.api.profile.TagManager;
import org.intermine.api.profile.TagManagerFactory;
import org.intermine.api.search.Scope;
import org.intermine.api.search.SearchRepository;
import org.intermine.api.template.TemplateQuery;
import org.intermine.model.userprofile.Tag;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.ObjectStoreWriterFactory;
import org.intermine.objectstore.intermine.ObjectStoreWriterInterMineImpl;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.ProfileBinding;
import org.intermine.web.logic.Constants;

import servletunit.ServletContextSimulator;

/**
 * Load template queries form an XML file into a given user profile.
 *
 * @author Thomas Riley
 */

public class LoadDefaultTemplatesTask extends Task
{
    private static final Logger LOG = Logger.getLogger(LoadDefaultTemplatesTask.class);

    protected static Random random = new Random();

    private String xmlFile;
    private String username;
    private String osAlias;
    private String superuserPassword;

    private String userProfileAlias;

    /**
     * Set the templates xml file.
     * @param file to xml file
     */
    public void setTemplatesXml(String file) {
        xmlFile = file;
    }

    /**
     * Set the account name to load template to.
     * @param user username to load templates into
     */
    public void setUsername(String user) {
        username = user;
    }

    /**
     * Set the alias of the main object store.
     * @param osAlias the object store alias
     */
    public void setOSAlias(String osAlias) {
        this.osAlias = osAlias;
    }

    /**
     * Set the alias of the userprofile object store.
     * @param userProfileAlias the object store alias of the userprofile database
     */
    public void setUserProfileAlias(String userProfileAlias) {
        this.userProfileAlias = userProfileAlias;
    }

    /**
     * Set the superuser's initial password. Make sure you change the password afterwards, because
     * passwords stored in properties are likely to be compromised - this is just a bootstrap.
     *
     * @param superuserPassword the initial superuser password
     */
    public void setSuperuserPassword(String superuserPassword) {
        this.superuserPassword = superuserPassword;
    }

    /**
     * Load templates from an xml file into a userprofile account.
     * {@inheritDoc}
     */
    public void execute() throws BuildException {
        log("Loading default templates and tags into profile " + username);

        // Needed so that STAX can find its implementation classes
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        ObjectStoreWriter osw = null;
        Profile profileDest = null;
        try {
            ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);
            ObjectStoreWriter userProfileOS =
                ObjectStoreWriterFactory.getObjectStoreWriter(userProfileAlias);
            
            ServletContext servletContext = new ServletContextSimulator();

            ProfileManager pm = new ProfileManager(os, userProfileOS);
            Reader reader = new FileReader(xmlFile);

            // Copy into existing or new superuser profile
            if (!pm.hasProfile(username)) {
                String password = superuserPassword;
                if ((password == null) || "".equals(password)
                        || "${superuser.initialPassword}".equals(password)) {
                    password = generatePassword();
                }
                LOG.info("Creating profile for " + username);
                profileDest = new Profile(pm, username, null, password,
                                      new HashMap(), new HashMap(), new HashMap());
                profileDest.disableSaving();
                pm.createProfile(profileDest);
            } else {
                LOG.info("Profile for " + username + ", clearing template queries");
                profileDest = pm.getProfile(username, pm.getPassword(username));
                Map tmpls = new HashMap(profileDest.getSavedTemplates());
                Iterator iter = tmpls.keySet().iterator();
                while (iter.hasNext()) {
                    profileDest.deleteTemplate((String) iter.next());
                }
            }

            // Settting global search repository to servletContext because unmarshall
            // method requires it
            servletContext.setAttribute(Constants.GLOBAL_SEARCH_REPOSITORY,
                    new SearchRepository(pm.getProfile(username), Scope.GLOBAL));

            // Unmarshal
            Set<Tag> tags = new HashSet();
            osw = new ObjectStoreWriterInterMineImpl(os);
            Profile profileSrc = ProfileBinding.unmarshal(reader, pm, profileDest.getUsername(),
                    profileDest.getPassword(), tags, osw, PathQuery.USERPROFILE_VERSION);

            if (profileDest.getSavedTemplates().size() == 0) {
                Iterator iter = profileSrc.getSavedTemplates().values().iterator();
                while (iter.hasNext()) {
                    TemplateQuery template = (TemplateQuery) iter.next();
                    String append = "";
                    if (!template.isValid()) {
                        append = " [invalid]";
                    }
                    log("Adding template \"" + template.getName() + "\"" + append);
                    profileDest.saveTemplate(template.getName(), template);
                }
                pm.convertTemplateKeywordsToTags(profileSrc.getSavedTemplates(), username);
            }

            // Tags not loaded automatically when unmarshalling profile
            TagManager tagManager = new TagManagerFactory(userProfileOS).getTagManager();
            for (Tag tag : tags) {
                 if (tagManager.getTags(tag.getTagName(), tag.getObjectIdentifier(),
                                            tag.getType(), profileDest.getUsername()).isEmpty()) {
                     try {
                         tagManager.addTag(tag.getTagName(), tag.getObjectIdentifier(),
                                           tag.getType(), profileDest.getUsername());
                     } catch (RuntimeException ex) {
                         LOG.error("Error happened during adding tag. Ignored. Tag: "
                                   + tag.toString(), ex);
                     }
                 }
             }

        } catch (Exception e) {
            LOG.error(e.getMessage());
            throw new BuildException(e);
        } finally {
            if (profileDest != null) {
                profileDest.enableSaving();
            }
            Thread.currentThread().setContextClassLoader(cl);
            try {
                if (osw != null) {
                    osw.close();
                }
            } catch (ObjectStoreException e) {
                // not much we can do here
                LOG.error("exception while closing object store writer", e);
            }
        }
    }

    /**
     * Generate a random 8-letter String of lower-case characters
     *
     * @return the String
     */
    public static String generatePassword() {
        String s = "";
        for (int i = 0; i < 8; i++) {
            s += (char) ('a' + random.nextInt(26));
        }
        return s;
    }
}
