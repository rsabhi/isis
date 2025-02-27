/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.isis.core.metamodel.facets.object.bookmarkpolicy;

import java.util.Map;

import org.apache.isis.applib.annotation.BookmarkPolicy;
import org.apache.isis.core.metamodel.facetapi.Facet;
import org.apache.isis.core.metamodel.facetapi.FacetAbstract;
import org.apache.isis.core.metamodel.facetapi.FacetHolder;

public abstract class BookmarkPolicyFacetAbstract 
extends FacetAbstract 
implements BookmarkPolicyFacet {

    public static Class<? extends Facet> type() {
        return BookmarkPolicyFacet.class;
    }

    private final BookmarkPolicy bookmarkPolicy;

    public BookmarkPolicyFacetAbstract(BookmarkPolicy bookmarkPolicy, FacetHolder facetHolder) {
        super(BookmarkPolicyFacetAbstract.type(), facetHolder, Derivation.NOT_DERIVED);
        this.bookmarkPolicy = bookmarkPolicy;
    }

    @Override
    public BookmarkPolicy value() {
        return bookmarkPolicy;
    }

    @Override public void appendAttributesTo(final Map<String, Object> attributeMap) {
        super.appendAttributesTo(attributeMap);
        attributeMap.put("bookmarkPolicy", bookmarkPolicy);
    }
}
