/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id: DOM2SAX.java 627367 2008-02-13 12:03:30Z maxberger $ */

package org.krugler.yalder.html;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.dom.DOMNodeHelper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Helper class that produces a SAX stream from a DOM Document.
 * <p>
 * Part of the code here copied and adapted from Apache Xalan-J, 
 * src/org/apache/xalan/xsltc/trax/DOM2SAX.java
 */
public class DOM2SAX {

    private static final String EMPTYSTRING = "";
    private static final String XMLNS_PREFIX = "xmlns";

    private ContentHandler contentHandler;
    private LexicalHandler lexicalHandler;
    
    private Map<String, Stack<String>> prefixes = new HashMap<>();
    
    /**
     * Main constructor
     * @param handler the ContentHandler to send SAX events to
     */
    public DOM2SAX(ContentHandler handler) {
        this.contentHandler = handler;
        if (handler instanceof LexicalHandler) {
            this.lexicalHandler = (LexicalHandler)handler;
        }
    }
    
    /**
     * Writes the given document using the given ContentHandler.
     * @param doc DOM document
     * @param fragment if false no startDocument() and endDocument() calls are issued.
     * @throws SAXException In case of a problem while writing XML
     */
    public void writeDocument(Document doc, boolean fragment) throws SAXException {
        if (!fragment) {
            contentHandler.startDocument();
        }
        
        Iterator<Node> iter = doc.nodeIterator();
        while (iter.hasNext()) {
            writeNode(iter.next());
        }

        if (!fragment) {
            contentHandler.endDocument();
        }
    }

    /**
     * Begin the scope of namespace prefix. Forward the event to the SAX handler
     * only if the prefix is unknown or it is mapped to a different URI.
     */
    private boolean startPrefixMapping(String prefix, String uri)
            throws SAXException {
        boolean pushed = true;
        Stack<String> uriStack = prefixes.get(prefix);

        if (uriStack != null) {
            if (uriStack.isEmpty()) {
                contentHandler.startPrefixMapping(prefix, uri);
                uriStack.push(uri);
            } else {
                final String lastUri = (String) uriStack.peek();
                if (!lastUri.equals(uri)) {
                    contentHandler.startPrefixMapping(prefix, uri);
                    uriStack.push(uri);
                } else {
                    pushed = false;
                }
            }
        } else {
            contentHandler.startPrefixMapping(prefix, uri);
            uriStack = new Stack<>();
            prefixes.put(prefix, uriStack);
            uriStack.push(uri);
        }
        return pushed;
    }

    /*
     * End the scope of a name prefix by popping it from the stack and passing
     * the event to the SAX Handler.
     */
    private void endPrefixMapping(String prefix) throws SAXException {
        final Stack<String> uriStack = prefixes.get(prefix);

        if (uriStack != null) {
            contentHandler.endPrefixMapping(prefix);
            uriStack.pop();
        }
    }

    /**
     * If the DOM was created using a DOM 1.0 API, the local name may be null.
     * If so, get the local name from the qualified name before generating the
     * SAX event.
     */
    private static String getLocalName(Node node) {
        final String localName = DOMNodeHelper.getLocalName(node);

        if (localName == null) {
            final String qname = node.getName();
            final int col = qname.lastIndexOf(':');
            return (col > 0) ? qname.substring(col + 1) : qname;
        }
        return localName;
    }

    /**
     * Writes a node using the given writer.
     * @param node node to serialize
     * @throws SAXException In case of a problem while writing XML
     */
    private void writeNode(Node node) 
                throws SAXException {
        if (node == null) {
            return;
        }

        switch (node.getNodeType()) {
        case Node.ATTRIBUTE_NODE: // handled by ELEMENT_NODE
        case Node.DOCUMENT_TYPE_NODE:
        case Node.ENTITY_REFERENCE_NODE:
            // These node types are ignored!!!
            break;
            
        case Node.CDATA_SECTION_NODE:
            final String cdata = node.getStringValue();
            if (lexicalHandler != null) {
                lexicalHandler.startCDATA();
                contentHandler.characters(cdata.toCharArray(), 0, cdata.length());
                lexicalHandler.endCDATA();
            } else {
                // in the case where there is no lex handler, we still
                // want the text of the cdate to make its way through.
                contentHandler.characters(cdata.toCharArray(), 0, cdata.length());
            }
            break;

        case Node.COMMENT_NODE: // should be handled!!!
            if (lexicalHandler != null) {
                final String value = node.getStringValue();
                lexicalHandler.comment(value.toCharArray(), 0, value.length());
            }
            break;
            
        case Node.DOCUMENT_NODE:
            contentHandler.startDocument();
            
            Iterator<Node> iter = ((Element)node).nodeIterator();
            while (iter.hasNext()) {
                writeNode(iter.next());
            }
            
            contentHandler.endDocument();
            break;

        case Node.ELEMENT_NODE:
            List<String> pushedPrefixes = new ArrayList<>();
            final AttributesImpl attrs = new AttributesImpl();
            
            Element e = (Element)node;

            // Process all namespace declarations
            for (Attribute attr : e.attributes()) {
                final String qnameAttr = attr.getName();
                
                // Ignore everything but NS declarations here
                if (qnameAttr.startsWith(XMLNS_PREFIX)) {
                    final String uriAttr = attr.getValue();
                    final int colon = qnameAttr.lastIndexOf(':');
                    String prefix = (colon > 0) ? qnameAttr.substring(colon + 1)
                            : EMPTYSTRING;
                    if (startPrefixMapping(prefix, uriAttr)) {
                        pushedPrefixes.add(prefix);
                    }
                }
            }

            // Process all other attributes
            for (Attribute attr : e.attributes()) {
                final String qnameAttr = attr.getName();

                // Ignore NS declarations here
                if (!qnameAttr.startsWith(XMLNS_PREFIX)) {
                    final String uriAttr = attr.getNamespaceURI();

                    // Uri may be implicitly declared
                    if (uriAttr != null) {
                        final int colon = qnameAttr.lastIndexOf(':');
                        String prefix = (colon > 0) ? qnameAttr.substring(0, colon)
                                : EMPTYSTRING;
                        if (startPrefixMapping(prefix, uriAttr)) {
                            pushedPrefixes.add(prefix);
                        }
                    }

                    // Add attribute to list
                    attrs.addAttribute(attr.getNamespaceURI(),
                            getLocalName(attr), qnameAttr, "CDATA", attr
                                    .getStringValue());
                }
            }

            // Now process the element itself
            final String qname = e.getName();
            final String uri = e.getNamespaceURI();
            final String localName = getLocalName(node);

            // Uri may be implicitly declared
            if (uri != null) {
                final int colon = qname.lastIndexOf(':');
                String prefix = (colon > 0) ? qname.substring(0, colon) : EMPTYSTRING;
                if (startPrefixMapping(prefix, uri)) {
                    pushedPrefixes.add(prefix);
                }
            }

            // Generate SAX event to start element
            contentHandler.startElement(uri, localName, qname, attrs);

            // Traverse all child nodes of the element (if any)
            Iterator<Node> nodeIter = e.nodeIterator();
            while (nodeIter.hasNext()) {
                writeNode(nodeIter.next());
            }

            // Generate SAX event to close element
            contentHandler.endElement(uri, localName, qname);

            // Generate endPrefixMapping() for all pushed prefixes
            final int nPushedPrefixes = pushedPrefixes.size();
            for (int i = 0; i < nPushedPrefixes; i++) {
                endPrefixMapping((String)pushedPrefixes.get(i));
            }
            break;

        case Node.PROCESSING_INSTRUCTION_NODE:
            contentHandler.processingInstruction(node.getName(), node.getStringValue());
            break;

        case Node.TEXT_NODE:
            final String data = node.getText();
            contentHandler.characters(data.toCharArray(), 0, data.length());
            break;
            
        default:
            //nop
        }
    }

    
}