package org.intermine.task.project;

import java.io.File;
import java.io.FileReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


public class ProjectXmlBinding
{

    public static Project unmarshall(File file) {
        try {
            FileReader reader = new FileReader(file);
            try {
                ProjectXmlHandler handler = new ProjectXmlHandler();
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setValidating(true);
                factory.newSAXParser().parse(new InputSource(reader), handler);
                return handler.project;
            } catch (ParserConfigurationException e) {
                throw new Exception("The underlying parser does not support "
                                    + " the requested features", e);
            } catch (SAXException e) {
                throw new Exception("Error parsing XML document", e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class ProjectXmlHandler extends DefaultHandler
    {
        Project project;
        Source source;
        PostProcess postProcess;
        //boolean postProcesses = false;

        /**
         * @see DefaultHandler#startElement
         */
        public void startElement(String uri, String localName, String qName, Attributes attrs)
            throws SAXException {
            if (qName.equals("project")) {
                project = new Project();
                if (attrs.getValue("type") == null) {
                    throw new IllegalArgumentException("project type must be set in project.xml");
                } else {
                    project.setType(attrs.getValue("type"));
                }
            } else if (qName.equals("source")) {
                source = new Source();
                source.setType(attrs.getValue("type"));
                project.addSource(attrs.getValue("name"), source);
            } else if (qName.equals("property")) {
                SourceProperty property = new SourceProperty();
                property.setName(attrs.getValue("name"));
                property.setValue(attrs.getValue("value"));
                property.setLocation(attrs.getValue("location"));
                if (source == null) {
                    project.addProperty(property);
                } else {
                    source.addProperty(property);
                }
            } else if (qName.equals("post-process")) {
                postProcess = new PostProcess();
                project.addPostProcess(attrs.getValue("name"), postProcess);
            }
        }

        /**
         * @see DefaultHandler#endElement
         */
        public void endElement(String uri, String localName, String qName) {
            if (qName.equals("source")) {
                source = null;
            } else if (qName.equals("post-process")) {
                postProcess = null;
            }
        }
    }
}
