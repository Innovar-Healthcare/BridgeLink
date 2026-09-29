/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model.converters;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.thoughtworks.xstream.io.xml.DomReader;

public class MirthDomReader extends DomReader {

    /*
     * The only constructor BridgeLink uses (ObjectXMLSerializer:348,405,421). The five other
     * DomReader constructor overloads this class used to mirror had no callers anywhere in the
     * tree, and two of them took the deprecated XmlFriendlyReplacer; they were removed rather than
     * left as untested surface that a future xstream bump would have to keep compiling.
     */
    public MirthDomReader(Element rootElement) {
        super(rootElement);
    }

    /*
     * MIRTH-3446 / IRT-1396 regression fix: prior to xstream 1.4.21, DomReader#reassignCurrentElement
     * eagerly rebuilt its internal child-element cache every time it was called, so calling
     * reassignCurrentElement(getCurrent()) here was sufficient to force DomReader to re-scan the
     * live DOM after MigratableConverter mutated it (added/removed child elements during
     * version migration). xstream 1.4.21 (GHI:#342, "Optimize internal handling of children in
     * DomReader avoiding O(n^2) access times for siblings") split that behavior: the private
     * childElements cache is now only rebuilt from DomReader#moveDown(), and
     * reassignCurrentElement() merely reassigns the current element reference without touching
     * the cache. Calling reassignCurrentElement(getCurrent()) is therefore now a no-op for reload
     * purposes, so migrated elements (e.g. Channel's migrate3_5_0, which removes
     * "codeTemplateLibraries" and adds "exportData") were silently deserialized against a STALE
     * child list, causing UnknownFieldException on removed legacy fields and dropping newly added
     * ones.
     *
     * Fix: override getChildCount()/getChild(int) (both declared protected in DomReader) so that
     * MirthDomReader never depends on that private, version-fragile cache at all — every access
     * re-reads the live DOM directly off the current element. This restores the pre-1.4.21
     * always-fresh behavior for our reader without reaching into xstream's private internals.
     *
     * Consequently there is no reload step and no reloadCurrentElement() helper any more: the old
     * helper (and MigratableConverter's call to it) was a provable no-op under 1.4.21 sitting under
     * a comment that claimed load-bearing behavior — exactly what leads a later maintainer to
     * conclude the reload path is still active and revert the overrides below. If a future xstream
     * ever restores rebuild-on-reassign semantics, the overrides below remain correct regardless.
     */
    @Override
    protected int getChildCount() {
        return getLiveChildElements().size();
    }

    @Override
    protected Object getChild(int index) {
        return getLiveChildElements().get(index);
    }

    private List<Element> getLiveChildElements() {
        List<Element> children = new ArrayList<Element>();
        NodeList nodeList = ((Element) getCurrent()).getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                children.add((Element) node);
            }
        }
        return children;
    }
}