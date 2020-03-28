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
package org.apache.isis.core.metamodel.inspect.model;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;

import org.apache.isis.applib.annotation.Navigable;
import org.apache.isis.applib.annotation.PropertyLayout;
import org.apache.isis.applib.annotation.Where;
import org.apache.isis.schema.metamodel.v2.DomainClassDto;

import lombok.Setter;

@XmlSeeAlso({        
    ActionNode.class,
    CollectionNode.class,
    FacetAttrNode.class,
    FacetNode.class,
    ParameterNode.class,
    PropertyNode.class,
    TypeNode.class,})
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class MMNode {
    
    @PropertyLayout(navigable=Navigable.PARENT, hidden=Where.EVERYWHERE)
    public abstract MMNode getParentNode();

    @Setter protected List<MMNode> childNodes;
    public List<MMNode> getChildNodes() {
        if(childNodes==null) {
            setChildNodes(streamChildNodes().collect(Collectors.toList()));
        }
        return childNodes;
    }
    
    protected abstract Stream<MMNode> streamChildNodes();

    protected String title;
    public final String title() {
        return title==null
                ? title = createTitle()
                : title;
    }
    public abstract String createTitle();

    public abstract String iconName();
    
    protected String typeToString(Object type) {
        if(type instanceof DomainClassDto) {
            return typeToString((DomainClassDto) type);
        }
        return type!=null
                ? abbreviate(""+type)
                : "void";
    }
    
    protected String typeToString(DomainClassDto type) {
        return type!=null
                ? abbreviate(type.getId())
                : "void";
    }

    public String abbreviate(String input) {
        return (""+input)
                .replace("org.apache.isis.core.metamodel.facets.", "».c.m.f.")
                .replace("org.apache.isis.core.metamodel.", "».c.m.")
                .replace("org.apache.isis.core.", "».c.")
                .replace("org.apache.isis.applib.", "».a.")
                .replace("org.apache.isis.", "».")
                .replace("java.lang.", "");
    }
    
}
